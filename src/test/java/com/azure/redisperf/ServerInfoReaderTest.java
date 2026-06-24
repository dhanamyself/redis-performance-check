package com.azure.redisperf;

import com.azure.redisperf.model.ServerInfo;
import com.azure.redisperf.service.ServerInfoReader;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerInfoReaderTest {

    private static final String SAMPLE_INFO =
            "# Server\r\n" +
            "redis_version:7.4.0\r\n" +
            "redis_mode:standalone\r\n" +
            "uptime_in_seconds:123456\r\n" +
            "# Clients\r\n" +
            "connected_clients:42\r\n" +
            "# Memory\r\n" +
            "used_memory:1048576\r\n" +
            "used_memory_human:1.00M\r\n" +
            "mem_fragmentation_ratio:1.25\r\n" +
            "# Stats\r\n" +
            "total_commands_processed:9999\r\n" +
            "keyspace_hits:800\r\n" +
            "keyspace_misses:200\r\n" +
            "evicted_keys:5\r\n" +
            "expired_keys:7\r\n" +
            "instantaneous_ops_per_sec:1500\r\n" +
            "# Replication\r\n" +
            "role:master\r\n" +
            "connected_slaves:1\r\n";

    @Test
    @SuppressWarnings("unchecked")
    void parsesKeyFields() {
        RedisCommands<String, String> cmd = mock(RedisCommands.class);
        when(cmd.info()).thenReturn(SAMPLE_INFO);

        ServerInfo info = new ServerInfoReader().read(cmd);

        assertEquals("7.4.0", info.getRedisVersion());
        assertEquals("standalone", info.getMode());
        assertEquals("master", info.getRole());
        assertEquals(123456, info.getUptimeSeconds());
        assertEquals(42, info.getConnectedClients());
        assertEquals(1048576, info.getUsedMemoryBytes());
        assertEquals("1.00M", info.getUsedMemoryHuman());
        assertEquals(1.25, info.getMemFragmentationRatio(), 0.001);
        assertEquals(800, info.getKeyspaceHits());
        assertEquals(200, info.getKeyspaceMisses());
        assertEquals(5, info.getEvictedKeys());
        assertEquals(7, info.getExpiredKeys());
        // Curated raw subset retained for the report.
        assertEquals("1", info.getRaw().get("connected_slaves"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toleratesMissingFields() {
        RedisCommands<String, String> cmd = mock(RedisCommands.class);
        when(cmd.info()).thenReturn("# Server\r\nredis_version:7.0.0\r\n");

        ServerInfo info = new ServerInfoReader().read(cmd);
        assertEquals("7.0.0", info.getRedisVersion());
        assertEquals(0, info.getUptimeSeconds());     // absent -> 0, no exception
        assertEquals(0, info.getConnectedClients());
    }
}
