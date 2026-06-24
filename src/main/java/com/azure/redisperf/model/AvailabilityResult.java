package com.azure.redisperf.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Availability / uptime probe results: continuous PINGs against the primary,
 * tracking failures, downtime windows and reconnection time.
 */
public class AvailabilityResult {
    private boolean enabled;
    private long totalPings;
    private long failedPings;
    private double availabilityPercent;
    private double pingMeanMs;
    private double pingP99Ms;
    private double pingMaxMs;
    private long outageCount;
    private double maxOutageMs;        // longest continuous unavailable window
    private double totalDowntimeMs;
    private final List<Outage> outages = new ArrayList<>();

    public static class Outage {
        private String startedAt;
        private double durationMs;
        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String v) { this.startedAt = v; }
        public double getDurationMs() { return durationMs; }
        public void setDurationMs(double v) { this.durationMs = v; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public long getTotalPings() { return totalPings; }
    public void setTotalPings(long v) { this.totalPings = v; }

    public long getFailedPings() { return failedPings; }
    public void setFailedPings(long v) { this.failedPings = v; }

    public double getAvailabilityPercent() { return availabilityPercent; }
    public void setAvailabilityPercent(double v) { this.availabilityPercent = v; }

    public double getPingMeanMs() { return pingMeanMs; }
    public void setPingMeanMs(double v) { this.pingMeanMs = v; }

    public double getPingP99Ms() { return pingP99Ms; }
    public void setPingP99Ms(double v) { this.pingP99Ms = v; }

    public double getPingMaxMs() { return pingMaxMs; }
    public void setPingMaxMs(double v) { this.pingMaxMs = v; }

    public long getOutageCount() { return outageCount; }
    public void setOutageCount(long v) { this.outageCount = v; }

    public double getMaxOutageMs() { return maxOutageMs; }
    public void setMaxOutageMs(double v) { this.maxOutageMs = v; }

    public double getTotalDowntimeMs() { return totalDowntimeMs; }
    public void setTotalDowntimeMs(double v) { this.totalDowntimeMs = v; }

    public List<Outage> getOutages() { return outages; }
}
