package com.azure.redisperf.model;

import org.HdrHistogram.Histogram;

/**
 * Immutable snapshot of latency percentiles (milliseconds) for a set of operations.
 */
public class LatencyStats {
    private final long count;
    private final double minMs;
    private final double meanMs;
    private final double p50Ms;
    private final double p90Ms;
    private final double p95Ms;
    private final double p99Ms;
    private final double p999Ms;
    private final double maxMs;

    private LatencyStats(long count, double minMs, double meanMs, double p50Ms, double p90Ms,
                         double p95Ms, double p99Ms, double p999Ms, double maxMs) {
        this.count = count;
        this.minMs = minMs;
        this.meanMs = meanMs;
        this.p50Ms = p50Ms;
        this.p90Ms = p90Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.p999Ms = p999Ms;
        this.maxMs = maxMs;
    }

    /** Histogram values are recorded in microseconds; convert to ms for reporting. */
    public static LatencyStats fromHistogram(Histogram h) {
        if (h.getTotalCount() == 0) {
            return new LatencyStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return new LatencyStats(
                h.getTotalCount(),
                h.getMinValue() / 1000.0,
                h.getMean() / 1000.0,
                h.getValueAtPercentile(50.0) / 1000.0,
                h.getValueAtPercentile(90.0) / 1000.0,
                h.getValueAtPercentile(95.0) / 1000.0,
                h.getValueAtPercentile(99.0) / 1000.0,
                h.getValueAtPercentile(99.9) / 1000.0,
                h.getMaxValue() / 1000.0
        );
    }

    public long getCount() { return count; }
    public double getMinMs() { return minMs; }
    public double getMeanMs() { return meanMs; }
    public double getP50Ms() { return p50Ms; }
    public double getP90Ms() { return p90Ms; }
    public double getP95Ms() { return p95Ms; }
    public double getP99Ms() { return p99Ms; }
    public double getP999Ms() { return p999Ms; }
    public double getMaxMs() { return maxMs; }
}
