package com.azure.redisperf.service;

import com.azure.redisperf.model.*;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates a full performance run:
 *  1. capture server INFO snapshot (before)
 *  2. start availability + replication probes (background, whole run)
 *  3. run the load engine once per payload size
 *  4. stop probes, capture INFO snapshot (after)
 *  5. write the standalone HTML report
 *
 * Only one run executes at a time. The in-progress / latest result is exposed
 * for the live UI to poll, and persisted runs are kept in memory by id.
 */
@Service
public class PerfTestService {

    private static final Logger log = LoggerFactory.getLogger(PerfTestService.class);
    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final LoadEngine loadEngine = new LoadEngine();
    private final ServerInfoReader infoReader = new ServerInfoReader();
    private final ReportService reportService;

    private final ExecutorService runExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "perf-run");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<TestResult> current = new AtomicReference<>();
    private final ConcurrentHashMap<String, TestResult> history = new ConcurrentHashMap<>();
    private volatile boolean busy = false;

    public PerfTestService(ReportService reportService) {
        this.reportService = reportService;
    }

    public synchronized String start(TestConfig cfg) {
        if (busy) {
            throw new IllegalStateException("A performance run is already in progress.");
        }
        if (cfg.getPrimaryHost() == null || cfg.getPrimaryHost().isBlank()) {
            throw new IllegalArgumentException("primaryHost is required.");
        }
        busy = true;

        String runId = UUID.randomUUID().toString().substring(0, 8);
        TestResult result = new TestResult();
        result.setRunId(runId);
        result.setLabel(cfg.getLabel());
        result.setStatus("RUNNING");
        result.setStartedAt(ISO.format(Instant.now()));
        result.setPrimaryEndpoint(cfg.getPrimaryHost() + ":" + cfg.getPrimaryPort());
        result.setSsl(cfg.isUseSsl());
        result.setConfig(TestResult.TestConfigSummary.from(cfg));
        current.set(result);
        history.put(runId, result);

        runExecutor.submit(() -> execute(cfg, result));
        return runId;
    }

    private void execute(TestConfig cfg, TestResult result) {
        AvailabilityProbe availabilityProbe = null;
        ReplicationProbe replicationProbe = null;
        Thread availThread = null, replThread = null;
        RedisConnections.Endpoint primary = null;
        RedisConnections.Endpoint replica = null;
        RedisConnections.Endpoint probePrimary = null;

        try {
            primary = RedisConnections.primary(cfg);

            // INFO snapshot (before) — also validates connectivity early.
            try (StatefulRedisConnection<String, String> conn = primary.newConnection()) {
                result.setServerInfoBefore(infoReader.read(conn.sync()));
            }

            // Availability probe (own endpoint/connection).
            if (cfg.isAvailabilityProbeEnabled()) {
                probePrimary = RedisConnections.primary(cfg);
                availabilityProbe = new AvailabilityProbe(probePrimary, cfg);
                availThread = new Thread(availabilityProbe, "availability-probe");
                availThread.setDaemon(true);
                availThread.start();
            }

            // Replication probe (only if a replica endpoint is configured).
            if (cfg.isReplicationProbeEnabled() && cfg.hasReplicaEndpoint()) {
                replica = RedisConnections.replica(cfg);
                replicationProbe = new ReplicationProbe(primary, replica, cfg);
                replThread = new Thread(replicationProbe, "replication-probe");
                replThread.setDaemon(true);
                replThread.start();
            } else if (cfg.isReplicationProbeEnabled()) {
                ReplicationResult rr = new ReplicationResult();
                rr.setEnabled(false);
                rr.setNote("No replica/secondary endpoint configured — replication lag skipped.");
                result.setReplication(rr);
            }

            // Payload sweep — one sub-run per configured size.
            for (Integer sizeKb : cfg.getPayloadSizesKb()) {
                PayloadResult pr = loadEngine.runPayload(primary, cfg, sizeKb);
                result.getPayloadResults().add(pr);
            }

            // Stop probes and collect their results.
            if (availabilityProbe != null) {
                availabilityProbe.stop();
                availThread.join(5000);
                result.setAvailability(availabilityProbe.getResult());
            }
            if (replicationProbe != null) {
                replicationProbe.stop();
                replThread.join(cfg.getReplicationTimeoutMs() + 5000L);
                result.setReplication(replicationProbe.getResult());
            }

            // INFO snapshot (after).
            try (StatefulRedisConnection<String, String> conn = primary.newConnection()) {
                result.setServerInfoAfter(infoReader.read(conn.sync()));
            }

            result.setStatus("COMPLETED");
            result.setFinishedAt(ISO.format(Instant.now()));

            String fileName = "report-" + FILE_TS.format(Instant.now()) + "-" + result.getRunId() + ".html";
            reportService.writeReport(result, fileName);
            result.setReportFileName(fileName);
            log.info("Run {} completed. Report: {}", result.getRunId(), fileName);

        } catch (Exception e) {
            log.error("Run {} failed", result.getRunId(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setFinishedAt(ISO.format(Instant.now()));
            if (availabilityProbe != null) availabilityProbe.stop();
            if (replicationProbe != null) replicationProbe.stop();
            try {
                String fileName = "report-" + FILE_TS.format(Instant.now()) + "-" + result.getRunId() + ".html";
                reportService.writeReport(result, fileName);
                result.setReportFileName(fileName);
            } catch (Exception ignored) { }
        } finally {
            closeQuietly(replica);
            closeQuietly(probePrimary);
            closeQuietly(primary);
            busy = false;
        }
    }

    private void closeQuietly(RedisConnections.Endpoint ep) {
        if (ep != null) {
            try { ep.close(); } catch (Exception ignored) { }
        }
    }

    public TestResult getCurrent() { return current.get(); }

    public TestResult getById(String runId) { return history.get(runId); }

    public boolean isBusy() { return busy; }
}
