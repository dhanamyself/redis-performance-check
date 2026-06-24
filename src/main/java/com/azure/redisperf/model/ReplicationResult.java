package com.azure.redisperf.model;

/**
 * Geo-replication lag results: time between a write on primary and the value
 * becoming readable on the replica/secondary endpoint.
 */
public class ReplicationResult {
    private boolean enabled;
    private String replicaEndpoint;
    private long samples;
    private long timeouts;        // writes that never appeared within replicationTimeoutMs
    private double minLagMs;
    private double meanLagMs;
    private double p50LagMs;
    private double p95LagMs;
    private double p99LagMs;
    private double maxLagMs;
    private String note;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public String getReplicaEndpoint() { return replicaEndpoint; }
    public void setReplicaEndpoint(String v) { this.replicaEndpoint = v; }

    public long getSamples() { return samples; }
    public void setSamples(long v) { this.samples = v; }

    public long getTimeouts() { return timeouts; }
    public void setTimeouts(long v) { this.timeouts = v; }

    public double getMinLagMs() { return minLagMs; }
    public void setMinLagMs(double v) { this.minLagMs = v; }

    public double getMeanLagMs() { return meanLagMs; }
    public void setMeanLagMs(double v) { this.meanLagMs = v; }

    public double getP50LagMs() { return p50LagMs; }
    public void setP50LagMs(double v) { this.p50LagMs = v; }

    public double getP95LagMs() { return p95LagMs; }
    public void setP95LagMs(double v) { this.p95LagMs = v; }

    public double getP99LagMs() { return p99LagMs; }
    public void setP99LagMs(double v) { this.p99LagMs = v; }

    public double getMaxLagMs() { return maxLagMs; }
    public void setMaxLagMs(double v) { this.maxLagMs = v; }

    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
}
