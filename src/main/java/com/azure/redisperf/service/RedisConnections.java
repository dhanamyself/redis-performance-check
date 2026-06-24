package com.azure.redisperf.service;

import com.azure.redisperf.model.TestConfig;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;

/**
 * Builds Lettuce clients/connections at runtime from a {@link TestConfig}.
 * Azure Managed Redis uses TLS (default port 10000) and an access key as the password.
 *
 * Each returned {@link Endpoint} owns a RedisClient; close it when done.
 */
public final class RedisConnections {

    private RedisConnections() {}

    public static class Endpoint implements AutoCloseable {
        private final RedisClient client;
        private final String description;

        Endpoint(RedisClient client, String description) {
            this.client = client;
            this.description = description;
        }

        public StatefulRedisConnection<String, String> newConnection() {
            return client.connect();
        }

        public String description() { return description; }

        @Override
        public void close() {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }

    private static RedisClient buildClient(String host, int port, String password, boolean ssl, int connectTimeoutMs) {
        RedisURI.Builder b = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withSsl(ssl)
                .withTimeout(Duration.ofMillis(connectTimeoutMs));
        if (password != null && !password.isBlank()) {
            b.withPassword(password.toCharArray());
        }
        RedisClient client = RedisClient.create(b.build());
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .keepAlive(true)
                        .build())
                .build());
        return client;
    }

    public static Endpoint primary(TestConfig cfg) {
        RedisClient c = buildClient(cfg.getPrimaryHost(), cfg.getPrimaryPort(), cfg.getPassword(),
                cfg.isUseSsl(), cfg.getConnectTimeoutMs());
        return new Endpoint(c, cfg.getPrimaryHost() + ":" + cfg.getPrimaryPort());
    }

    public static Endpoint replica(TestConfig cfg) {
        RedisClient c = buildClient(cfg.getReplicaHost(), cfg.getReplicaPort(), cfg.effectiveReplicaPassword(),
                cfg.isUseSsl(), cfg.getConnectTimeoutMs());
        return new Endpoint(c, cfg.getReplicaHost() + ":" + cfg.getReplicaPort());
    }
}
