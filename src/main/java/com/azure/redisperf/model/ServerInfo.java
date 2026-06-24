package com.azure.redisperf.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Selected fields from Redis INFO, captured before and after the run.
 * Includes server-side uptime, role and memory KPIs.
 */
public class ServerInfo {
    private String redisVersion;
    private String mode;            // standalone / cluster
    private String role;            // master / replica
    private long uptimeSeconds;
    private long connectedClients;
    private String usedMemoryHuman;
    private long usedMemoryBytes;
    private double memFragmentationRatio;
    private long totalConnectionsReceived;
    private long totalCommandsProcessed;
    private long keyspaceHits;
    private long keyspaceMisses;
    private long evictedKeys;
    private long expiredKeys;
    private double instantaneousOpsPerSec;
    private final Map<String, String> raw = new LinkedHashMap<>();

    public String getRedisVersion() { return redisVersion; }
    public void setRedisVersion(String v) { this.redisVersion = v; }

    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }

    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }

    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long v) { this.uptimeSeconds = v; }

    public long getConnectedClients() { return connectedClients; }
    public void setConnectedClients(long v) { this.connectedClients = v; }

    public String getUsedMemoryHuman() { return usedMemoryHuman; }
    public void setUsedMemoryHuman(String v) { this.usedMemoryHuman = v; }

    public long getUsedMemoryBytes() { return usedMemoryBytes; }
    public void setUsedMemoryBytes(long v) { this.usedMemoryBytes = v; }

    public double getMemFragmentationRatio() { return memFragmentationRatio; }
    public void setMemFragmentationRatio(double v) { this.memFragmentationRatio = v; }

    public long getTotalConnectionsReceived() { return totalConnectionsReceived; }
    public void setTotalConnectionsReceived(long v) { this.totalConnectionsReceived = v; }

    public long getTotalCommandsProcessed() { return totalCommandsProcessed; }
    public void setTotalCommandsProcessed(long v) { this.totalCommandsProcessed = v; }

    public long getKeyspaceHits() { return keyspaceHits; }
    public void setKeyspaceHits(long v) { this.keyspaceHits = v; }

    public long getKeyspaceMisses() { return keyspaceMisses; }
    public void setKeyspaceMisses(long v) { this.keyspaceMisses = v; }

    public long getEvictedKeys() { return evictedKeys; }
    public void setEvictedKeys(long v) { this.evictedKeys = v; }

    public long getExpiredKeys() { return expiredKeys; }
    public void setExpiredKeys(long v) { this.expiredKeys = v; }

    public double getInstantaneousOpsPerSec() { return instantaneousOpsPerSec; }
    public void setInstantaneousOpsPerSec(double v) { this.instantaneousOpsPerSec = v; }

    public Map<String, String> getRaw() { return raw; }
}
