package com.azure.redisperf.service;

import com.azure.redisperf.model.AvailabilityResult;
import com.azure.redisperf.model.TestConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Continuously PINGs the primary endpoint to measure availability/uptime and
 * detect outage windows + reconnection time. Runs on its own dedicated connection
 * so it is not affected by the load workers.
 */
public class AvailabilityProbe implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityProbe.class);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final RedisConnections.Endpoint endpoint;
    private final TestConfig cfg;
    private volatile boolean running = true;

    private final Histogram pingHist = new Histogram(1, 60_000_000L, 3); // microseconds
    private final AvailabilityResult result = new AvailabilityResult();

    public AvailabilityProbe(RedisConnections.Endpoint endpoint, TestConfig cfg) {
        this.endpoint = endpoint;
        this.cfg = cfg;
        this.result.setEnabled(true);
    }

    public void stop() { this.running = false; }

    public AvailabilityResult getResult() { return result; }

    @Override
    public void run() {
        long total = 0, failed = 0, outages = 0;
        double maxOutage = 0, totalDowntime = 0;
        long outageStartNs = 0;
        boolean inOutage = false;

        try (StatefulRedisConnection<String, String> conn = endpoint.newConnection()) {
            RedisCommands<String, String> cmd = conn.sync();
            while (running) {
                long start = System.nanoTime();
                boolean ok;
                try {
                    String pong = cmd.ping();
                    ok = "PONG".equalsIgnoreCase(pong);
                } catch (Exception e) {
                    ok = false;
                }
                long elapsedNs = System.nanoTime() - start;
                total++;

                if (ok) {
                    pingHist.recordValue(Math.min(elapsedNs / 1000, 60_000_000L));
                    if (inOutage) {
                        double durMs = (System.nanoTime() - outageStartNs) / 1_000_000.0;
                        recordOutage(outageStartNs, durMs);
                        maxOutage = Math.max(maxOutage, durMs);
                        totalDowntime += durMs;
                        outages++;
                        inOutage = false;
                    }
                } else {
                    failed++;
                    if (!inOutage) {
                        inOutage = true;
                        outageStartNs = start;
                    }
                }

                sleep(cfg.getAvailabilityProbeIntervalMs());
            }

            // Close out an outage that was still open when the test ended.
            if (inOutage) {
                double durMs = (System.nanoTime() - outageStartNs) / 1_000_000.0;
                recordOutage(outageStartNs, durMs);
                maxOutage = Math.max(maxOutage, durMs);
                totalDowntime += durMs;
                outages++;
            }
        } catch (Exception e) {
            log.warn("Availability probe terminated: {}", e.toString());
        }

        result.setTotalPings(total);
        result.setFailedPings(failed);
        result.setAvailabilityPercent(total == 0 ? 0 : (total - failed) * 100.0 / total);
        result.setPingMeanMs(pingHist.getTotalCount() == 0 ? 0 : pingHist.getMean() / 1000.0);
        result.setPingP99Ms(pingHist.getTotalCount() == 0 ? 0 : pingHist.getValueAtPercentile(99.0) / 1000.0);
        result.setPingMaxMs(pingHist.getTotalCount() == 0 ? 0 : pingHist.getMaxValue() / 1000.0);
        result.setOutageCount(outages);
        result.setMaxOutageMs(maxOutage);
        result.setTotalDowntimeMs(totalDowntime);
    }

    private void recordOutage(long startNs, double durMs) {
        AvailabilityResult.Outage o = new AvailabilityResult.Outage();
        // Approximate wall-clock start from the monotonic delta.
        long approxStartMs = System.currentTimeMillis() - (long) ((System.nanoTime() - startNs) / 1_000_000.0);
        o.setStartedAt(TS.format(Instant.ofEpochMilli(approxStartMs)));
        o.setDurationMs(durMs);
        result.getOutages().add(o);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); running = false; }
    }
}
