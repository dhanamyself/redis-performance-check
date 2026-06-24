package com.azure.redisperf.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete result of a performance run, serialized to JSON for the live UI
 * and rendered into a standalone HTML report.
 */
public class TestResult {
    private String runId;
    private String label;
    private String startedAt;
    private String finishedAt;
    private String status;                 // RUNNING / COMPLETED / FAILED
    private String errorMessage;

    private String primaryEndpoint;
    private boolean ssl;

    private final List<PayloadResult> payloadResults = new ArrayList<>();
    private ReplicationResult replication;
    private AvailabilityResult availability;
    private ServerInfo serverInfoBefore;
    private ServerInfo serverInfoAfter;

    // Echo of the config used (without secrets) for the report header.
    private TestConfigSummary config;

    private String reportFileName;         // generated HTML report filename

    public String getRunId() { return runId; }
    public void setRunId(String v) { this.runId = v; }

    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String v) { this.startedAt = v; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String v) { this.finishedAt = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    public String getPrimaryEndpoint() { return primaryEndpoint; }
    public void setPrimaryEndpoint(String v) { this.primaryEndpoint = v; }

    public boolean isSsl() { return ssl; }
    public void setSsl(boolean v) { this.ssl = v; }

    public List<PayloadResult> getPayloadResults() { return payloadResults; }

    public ReplicationResult getReplication() { return replication; }
    public void setReplication(ReplicationResult v) { this.replication = v; }

    public AvailabilityResult getAvailability() { return availability; }
    public void setAvailability(AvailabilityResult v) { this.availability = v; }

    public ServerInfo getServerInfoBefore() { return serverInfoBefore; }
    public void setServerInfoBefore(ServerInfo v) { this.serverInfoBefore = v; }

    public ServerInfo getServerInfoAfter() { return serverInfoAfter; }
    public void setServerInfoAfter(ServerInfo v) { this.serverInfoAfter = v; }

    public TestConfigSummary getConfig() { return config; }
    public void setConfig(TestConfigSummary v) { this.config = v; }

    public String getReportFileName() { return reportFileName; }
    public void setReportFileName(String v) { this.reportFileName = v; }

    /** Config echo without the password. */
    public static class TestConfigSummary {
        public int durationSeconds;
        public int warmupSeconds;
        public int concurrency;
        public int readRatioPercent;
        public int keyspaceSize;
        public int sessionTtlSeconds;
        public List<Integer> payloadSizesKb;
        public boolean replicationProbeEnabled;
        public boolean availabilityProbeEnabled;
        public String replicaEndpoint;

        public static TestConfigSummary from(TestConfig c) {
            TestConfigSummary s = new TestConfigSummary();
            s.durationSeconds = c.getDurationSeconds();
            s.warmupSeconds = c.getWarmupSeconds();
            s.concurrency = c.getConcurrency();
            s.readRatioPercent = c.getReadRatioPercent();
            s.keyspaceSize = c.getKeyspaceSize();
            s.sessionTtlSeconds = c.getSessionTtlSeconds();
            s.payloadSizesKb = c.getPayloadSizesKb();
            s.replicationProbeEnabled = c.isReplicationProbeEnabled();
            s.availabilityProbeEnabled = c.isAvailabilityProbeEnabled();
            s.replicaEndpoint = c.hasReplicaEndpoint() ? c.getReplicaHost() + ":" + c.getReplicaPort() : null;
            return s;
        }
    }
}
