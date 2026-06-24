package com.azure.redisperf.service;

import com.azure.redisperf.model.LatencyStats;
import com.azure.redisperf.model.PayloadResult;
import com.azure.redisperf.model.TestConfig;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the concurrent GET/PUT load for a single payload size and collects
 * latency percentiles + throughput. Each worker uses its own connection and
 * its own histograms (merged at the end) to avoid contention.
 */
public class LoadEngine {

    private static final Logger log = LoggerFactory.getLogger(LoadEngine.class);
    private static final String KEY_PREFIX = "redisperf:session:";

    private final SessionPayloadFactory payloadFactory = new SessionPayloadFactory();

    public PayloadResult runPayload(RedisConnections.Endpoint endpoint, TestConfig cfg, int sizeKb) throws InterruptedException {
        int targetBytes = sizeKb * 1024;
        int actualBytes = payloadFactory.build("probe", targetBytes).byteLength();

        // Seed the keyspace so GETs hit existing keys from the very start.
        seedKeyspace(endpoint, cfg, targetBytes);

        int threads = Math.max(1, cfg.getConcurrency());
        AtomicBoolean measuring = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);

        List<Worker> workers = new ArrayList<>(threads);
        List<Thread> pool = new ArrayList<>(threads);
        CountDownLatch ready = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Worker w = new Worker(endpoint, cfg, targetBytes, measuring, stop, ready);
            workers.add(w);
            Thread t = new Thread(w, "load-" + sizeKb + "kb-" + i);
            pool.add(t);
            t.start();
        }
        ready.await(); // all workers connected

        // Warmup (not measured)
        if (cfg.getWarmupSeconds() > 0) {
            Thread.sleep(cfg.getWarmupSeconds() * 1000L);
        }

        // Measurement window
        long measureStart = System.nanoTime();
        measuring.set(true);
        Thread.sleep(cfg.getDurationSeconds() * 1000L);
        measuring.set(false);
        stop.set(true);
        double measuredSeconds = (System.nanoTime() - measureStart) / 1_000_000_000.0;

        for (Thread t : pool) t.join();

        // Merge per-worker histograms.
        Histogram getHist = new Histogram(1, 120_000_000L, 3);
        Histogram putHist = new Histogram(1, 120_000_000L, 3);
        Histogram allHist = new Histogram(1, 120_000_000L, 3);
        long getOps = 0, putOps = 0, errors = 0;
        for (Worker w : workers) {
            getHist.add(w.getHist);
            putHist.add(w.putHist);
            allHist.add(w.getHist);
            allHist.add(w.putHist);
            getOps += w.getHist.getTotalCount();
            putOps += w.putHist.getTotalCount();
            errors += w.errors;
        }

        PayloadResult pr = new PayloadResult();
        pr.setPayloadSizeKb(sizeKb);
        pr.setActualPayloadBytes(actualBytes);
        pr.setDurationSeconds(measuredSeconds);
        pr.setGetOps(getOps);
        pr.setPutOps(putOps);
        pr.setTotalOps(getOps + putOps);
        pr.setErrors(errors);
        pr.setThroughputOpsPerSec((getOps + putOps) / measuredSeconds);
        pr.setGetThroughputOpsPerSec(getOps / measuredSeconds);
        pr.setPutThroughputOpsPerSec(putOps / measuredSeconds);
        pr.setOverallLatency(LatencyStats.fromHistogram(allHist));
        pr.setGetLatency(LatencyStats.fromHistogram(getHist));
        pr.setPutLatency(LatencyStats.fromHistogram(putHist));

        log.info("Payload {}KB done: {} ops, {} ops/sec, p99={}ms",
                sizeKb, pr.getTotalOps(), String.format("%.0f", pr.getThroughputOpsPerSec()),
                String.format("%.2f", pr.getOverallLatency().getP99Ms()));
        return pr;
    }

    private void seedKeyspace(RedisConnections.Endpoint endpoint, TestConfig cfg, int targetBytes) {
        try (StatefulRedisConnection<String, String> conn = endpoint.newConnection()) {
            RedisCommands<String, String> cmd = conn.sync();
            int seed = Math.min(cfg.getKeyspaceSize(), 2000); // cap seeding cost; remainder filled by load
            for (int i = 0; i < seed; i++) {
                String key = KEY_PREFIX + i;
                String json = payloadFactory.build(key, targetBytes).json();
                if (cfg.getSessionTtlSeconds() > 0) {
                    cmd.set(key, json, SetArgs.Builder.ex(cfg.getSessionTtlSeconds()));
                } else {
                    cmd.set(key, json);
                }
            }
        } catch (Exception e) {
            log.warn("Keyspace seeding failed (continuing): {}", e.toString());
        }
    }

    /** One load worker on its own connection. */
    private static class Worker implements Runnable {
        final RedisConnections.Endpoint endpoint;
        final TestConfig cfg;
        final int targetBytes;
        final AtomicBoolean measuring;
        final AtomicBoolean stop;
        final CountDownLatch ready;
        final SessionPayloadFactory factory = new SessionPayloadFactory();

        final Histogram getHist = new Histogram(1, 120_000_000L, 3);
        final Histogram putHist = new Histogram(1, 120_000_000L, 3);
        long errors = 0;

        Worker(RedisConnections.Endpoint endpoint, TestConfig cfg, int targetBytes,
               AtomicBoolean measuring, AtomicBoolean stop, CountDownLatch ready) {
            this.endpoint = endpoint;
            this.cfg = cfg;
            this.targetBytes = targetBytes;
            this.measuring = measuring;
            this.stop = stop;
            this.ready = ready;
        }

        @Override
        public void run() {
            try (StatefulRedisConnection<String, String> conn = endpoint.newConnection()) {
                RedisCommands<String, String> cmd = conn.sync();
                ready.countDown();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (!stop.get()) {
                    int k = rnd.nextInt(cfg.getKeyspaceSize());
                    String key = KEY_PREFIX + k;
                    boolean isRead = rnd.nextInt(100) < cfg.getReadRatioPercent();
                    long start = System.nanoTime();
                    try {
                        if (isRead) {
                            cmd.get(key);
                            recordIfMeasuring(getHist, start);
                        } else {
                            String json = factory.build(key, targetBytes).json();
                            if (cfg.getSessionTtlSeconds() > 0) {
                                cmd.set(key, json, SetArgs.Builder.ex(cfg.getSessionTtlSeconds()));
                            } else {
                                cmd.set(key, json);
                            }
                            recordIfMeasuring(putHist, start);
                        }
                    } catch (Exception e) {
                        if (measuring.get()) errors++;
                    }
                }
            } catch (Exception e) {
                ready.countDown(); // unblock orchestrator even if connect failed
                if (measuring.get()) errors++;
            }
        }

        private void recordIfMeasuring(Histogram h, long startNs) {
            if (measuring.get()) {
                long us = (System.nanoTime() - startNs) / 1000;
                h.recordValue(Math.min(us, 120_000_000L));
            }
        }
    }
}
