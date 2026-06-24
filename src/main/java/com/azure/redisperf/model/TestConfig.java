package com.azure.redisperf.model;

import java.util.List;

/**
 * Full configuration for a performance run, posted from the HTML UI.
 * All fields have sane defaults so a minimal request (just connection info) still works.
 */
public class TestConfig {

    // ---- Connection (primary / writable endpoint) ----
    private String primaryHost;
    private int primaryPort = 10000;          // Azure Managed Redis default TLS port
    private String password;                  // access key
    private boolean useSsl = true;            // Azure Managed Redis requires TLS
    private int connectTimeoutMs = 10_000;

    // ---- Optional secondary / replica endpoint (for geo-replication lag) ----
    private String replicaHost;               // blank => replication test skipped
    private int replicaPort = 10000;
    private String replicaPassword;           // blank => reuse primary password

    // ---- Load shape ----
    private int durationSeconds = 30;         // length of the steady-state load phase
    private int warmupSeconds = 5;
    private int concurrency = 16;             // worker threads
    private int readRatioPercent = 80;        // % GET vs PUT (remainder is PUT)
    private int keyspaceSize = 10_000;        // number of distinct session keys
    private int sessionTtlSeconds = 1800;     // TTL applied to session keys (0 = no expiry)

    // ---- Payload sweep: session JSON sizes in KB. One sub-run per size. ----
    private List<Integer> payloadSizesKb = List.of(1, 10, 100);

    // ---- Probes (run for the whole test duration on dedicated connections) ----
    private boolean replicationProbeEnabled = true;
    private int replicationProbeIntervalMs = 1000;
    private int replicationTimeoutMs = 5000;  // give up waiting for a key to appear on replica

    private boolean availabilityProbeEnabled = true;
    private int availabilityProbeIntervalMs = 500;

    private String label = "redis-perf-run";  // shown in the report

    // ---- getters / setters ----
    public String getPrimaryHost() { return primaryHost; }
    public void setPrimaryHost(String primaryHost) { this.primaryHost = primaryHost; }

    public int getPrimaryPort() { return primaryPort; }
    public void setPrimaryPort(int primaryPort) { this.primaryPort = primaryPort; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isUseSsl() { return useSsl; }
    public void setUseSsl(boolean useSsl) { this.useSsl = useSsl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public String getReplicaHost() { return replicaHost; }
    public void setReplicaHost(String replicaHost) { this.replicaHost = replicaHost; }

    public int getReplicaPort() { return replicaPort; }
    public void setReplicaPort(int replicaPort) { this.replicaPort = replicaPort; }

    public String getReplicaPassword() { return replicaPassword; }
    public void setReplicaPassword(String replicaPassword) { this.replicaPassword = replicaPassword; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getWarmupSeconds() { return warmupSeconds; }
    public void setWarmupSeconds(int warmupSeconds) { this.warmupSeconds = warmupSeconds; }

    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

    public int getReadRatioPercent() { return readRatioPercent; }
    public void setReadRatioPercent(int readRatioPercent) { this.readRatioPercent = readRatioPercent; }

    public int getKeyspaceSize() { return keyspaceSize; }
    public void setKeyspaceSize(int keyspaceSize) { this.keyspaceSize = keyspaceSize; }

    public int getSessionTtlSeconds() { return sessionTtlSeconds; }
    public void setSessionTtlSeconds(int sessionTtlSeconds) { this.sessionTtlSeconds = sessionTtlSeconds; }

    public List<Integer> getPayloadSizesKb() { return payloadSizesKb; }
    public void setPayloadSizesKb(List<Integer> payloadSizesKb) { this.payloadSizesKb = payloadSizesKb; }

    public boolean isReplicationProbeEnabled() { return replicationProbeEnabled; }
    public void setReplicationProbeEnabled(boolean v) { this.replicationProbeEnabled = v; }

    public int getReplicationProbeIntervalMs() { return replicationProbeIntervalMs; }
    public void setReplicationProbeIntervalMs(int v) { this.replicationProbeIntervalMs = v; }

    public int getReplicationTimeoutMs() { return replicationTimeoutMs; }
    public void setReplicationTimeoutMs(int v) { this.replicationTimeoutMs = v; }

    public boolean isAvailabilityProbeEnabled() { return availabilityProbeEnabled; }
    public void setAvailabilityProbeEnabled(boolean v) { this.availabilityProbeEnabled = v; }

    public int getAvailabilityProbeIntervalMs() { return availabilityProbeIntervalMs; }
    public void setAvailabilityProbeIntervalMs(int v) { this.availabilityProbeIntervalMs = v; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean hasReplicaEndpoint() {
        return replicaHost != null && !replicaHost.isBlank();
    }

    public String effectiveReplicaPassword() {
        return (replicaPassword != null && !replicaPassword.isBlank()) ? replicaPassword : password;
    }
}
