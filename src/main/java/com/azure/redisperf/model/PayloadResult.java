package com.azure.redisperf.model;

/**
 * Results for one payload-size sub-run of the load test.
 */
public class PayloadResult {
    private int payloadSizeKb;
    private int actualPayloadBytes;
    private long totalOps;
    private long getOps;
    private long putOps;
    private long errors;
    private double durationSeconds;
    private double throughputOpsPerSec;
    private double getThroughputOpsPerSec;
    private double putThroughputOpsPerSec;

    private LatencyStats overallLatency;
    private LatencyStats getLatency;
    private LatencyStats putLatency;

    public int getPayloadSizeKb() { return payloadSizeKb; }
    public void setPayloadSizeKb(int v) { this.payloadSizeKb = v; }

    public int getActualPayloadBytes() { return actualPayloadBytes; }
    public void setActualPayloadBytes(int v) { this.actualPayloadBytes = v; }

    public long getTotalOps() { return totalOps; }
    public void setTotalOps(long v) { this.totalOps = v; }

    public long getGetOps() { return getOps; }
    public void setGetOps(long v) { this.getOps = v; }

    public long getPutOps() { return putOps; }
    public void setPutOps(long v) { this.putOps = v; }

    public long getErrors() { return errors; }
    public void setErrors(long v) { this.errors = v; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double v) { this.durationSeconds = v; }

    public double getThroughputOpsPerSec() { return throughputOpsPerSec; }
    public void setThroughputOpsPerSec(double v) { this.throughputOpsPerSec = v; }

    public double getGetThroughputOpsPerSec() { return getThroughputOpsPerSec; }
    public void setGetThroughputOpsPerSec(double v) { this.getThroughputOpsPerSec = v; }

    public double getPutThroughputOpsPerSec() { return putThroughputOpsPerSec; }
    public void setPutThroughputOpsPerSec(double v) { this.putThroughputOpsPerSec = v; }

    public LatencyStats getOverallLatency() { return overallLatency; }
    public void setOverallLatency(LatencyStats v) { this.overallLatency = v; }

    public LatencyStats getGetLatency() { return getLatency; }
    public void setGetLatency(LatencyStats v) { this.getLatency = v; }

    public LatencyStats getPutLatency() { return putLatency; }
    public void setPutLatency(LatencyStats v) { this.putLatency = v; }
}
