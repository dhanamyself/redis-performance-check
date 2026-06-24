package com.azure.redisperf.service;

import com.azure.redisperf.model.ServerInfo;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the output of Redis {@code INFO} into a {@link ServerInfo} snapshot.
 */
public class ServerInfoReader {

    public ServerInfo read(RedisCommands<String, String> cmd) {
        String raw = cmd.info();
        Map<String, String> kv = parse(raw);

        ServerInfo info = new ServerInfo();
        info.setRedisVersion(kv.get("redis_version"));
        info.setMode(kv.get("redis_mode"));
        info.setRole(kv.get("role"));
        info.setUptimeSeconds(asLong(kv.get("uptime_in_seconds")));
        info.setConnectedClients(asLong(kv.get("connected_clients")));
        info.setUsedMemoryHuman(kv.get("used_memory_human"));
        info.setUsedMemoryBytes(asLong(kv.get("used_memory")));
        info.setMemFragmentationRatio(asDouble(kv.get("mem_fragmentation_ratio")));
        info.setTotalConnectionsReceived(asLong(kv.get("total_connections_received")));
        info.setTotalCommandsProcessed(asLong(kv.get("total_commands_processed")));
        info.setKeyspaceHits(asLong(kv.get("keyspace_hits")));
        info.setKeyspaceMisses(asLong(kv.get("keyspace_misses")));
        info.setEvictedKeys(asLong(kv.get("evicted_keys")));
        info.setExpiredKeys(asLong(kv.get("expired_keys")));
        info.setInstantaneousOpsPerSec(asDouble(kv.get("instantaneous_ops_per_sec")));

        // Keep a curated subset of raw fields for the report's "raw INFO" section.
        for (String key : new String[]{
                "redis_version", "redis_mode", "role", "os", "uptime_in_days",
                "connected_clients", "blocked_clients", "maxmemory_human", "used_memory_peak_human",
                "rdb_last_save_time", "aof_enabled", "connected_slaves", "master_repl_offset",
                "total_net_input_bytes", "total_net_output_bytes", "rejected_connections",
                "sync_full", "sync_partial_ok", "sync_partial_err"}) {
            if (kv.containsKey(key)) {
                info.getRaw().put(key, kv.get(key));
            }
        }
        return info;
    }

    private Map<String, String> parse(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) return map;
        for (String line : raw.split("\r?\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            int idx = line.indexOf(':');
            if (idx > 0) {
                map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }
        return map;
    }

    private long asLong(String s) {
        try { return s == null ? 0 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private double asDouble(String s) {
        try { return s == null ? 0 : Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
