package com.azure.redisperf.service;

import com.azure.redisperf.model.ReplicationResult;
import com.azure.redisperf.model.TestConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures geo-replication lag: writes a uniquely-versioned marker key to the
 * primary, then busy-polls the replica endpoint until that exact value appears,
 * recording the elapsed time. Runs on dedicated connections for the test duration.
 *
 * Note: for active-active Azure Managed Redis you may point "replica" at the
 * secondary region's endpoint. If both endpoints are the same the lag will be ~0.
 */
public class ReplicationProbe implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicationProbe.class);
    private static final String KEY = "redisperf:replication:marker";

    private final RedisConnections.Endpoint primary;
    private final RedisConnections.Endpoint replica;
    private final TestConfig cfg;
    private volatile boolean running = true;

    private final Histogram lagHist = new Histogram(1, 60_000_000L, 3); // microseconds
    private final ReplicationResult result = new ReplicationResult();

    public ReplicationProbe(RedisConnections.Endpoint primary, RedisConnections.Endpoint replica, TestConfig cfg) {
        this.primary = primary;
        this.replica = replica;
        this.cfg = cfg;
        result.setEnabled(true);
        result.setReplicaEndpoint(replica.description());
    }

    public void stop() { this.running = false; }

    public ReplicationResult getResult() { return result; }

    @Override
    public void run() {
        long samples = 0, timeouts = 0;
        long seq = 0;

        try (StatefulRedisConnection<String, String> pConn = primary.newConnection();
             StatefulRedisConnection<String, String> rConn = replica.newConnection()) {

            RedisCommands<String, String> p = pConn.sync();
            RedisCommands<String, String> r = rConn.sync();

            while (running) {
                String value = "v" + (seq++) + "-" + System.nanoTime();
                long start = System.nanoTime();
                try {
                    p.set(KEY, value);
                } catch (Exception e) {
                    sleep(cfg.getReplicationProbeIntervalMs());
                    continue;
                }

                boolean appeared = false;
                long deadline = start + cfg.getReplicationTimeoutMs() * 1_000_000L;
                while (System.nanoTime() < deadline && running) {
                    try {
                        if (value.equals(r.get(KEY))) {
                            appeared = true;
                            break;
                        }
                    } catch (Exception ignored) {
                        // transient replica read error; keep polling until deadline
                    }
                    // Tight-ish poll; small sleep to avoid hammering.
                    parkMicros(500);
                }

                if (appeared) {
                    long lagUs = (System.nanoTime() - start) / 1000;
                    lagHist.recordValue(Math.min(lagUs, 60_000_000L));
                    samples++;
                } else {
                    timeouts++;
                }

                sleep(cfg.getReplicationProbeIntervalMs());
            }
        } catch (Exception e) {
            log.warn("Replication probe terminated: {}", e.toString());
            result.setNote("Probe error: " + e.getMessage());
        }

        result.setSamples(samples);
        result.setTimeouts(timeouts);
        if (lagHist.getTotalCount() > 0) {
            result.setMinLagMs(lagHist.getMinValue() / 1000.0);
            result.setMeanLagMs(lagHist.getMean() / 1000.0);
            result.setP50LagMs(lagHist.getValueAtPercentile(50.0) / 1000.0);
            result.setP95LagMs(lagHist.getValueAtPercentile(95.0) / 1000.0);
            result.setP99LagMs(lagHist.getValueAtPercentile(99.0) / 1000.0);
            result.setMaxLagMs(lagHist.getMaxValue() / 1000.0);
        } else if (result.getNote() == null) {
            result.setNote("No replication samples completed within timeout.");
        }
    }

    private void parkMicros(long micros) {
        java.util.concurrent.locks.LockSupport.parkNanos(micros * 1000);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); running = false; }
    }
}
