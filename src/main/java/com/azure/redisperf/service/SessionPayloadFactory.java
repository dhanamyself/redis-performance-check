package com.azure.redisperf.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds realistic session JSON documents padded to an approximate target size.
 * This mimics the "store/retrieve session JSON" workload the user cares about.
 */
public class SessionPayloadFactory {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Produce a session JSON string of approximately {@code targetBytes} bytes.
     * Returns the JSON and the actual byte length.
     */
    public Payload build(String sessionId, int targetBytes) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("sessionId", sessionId);
        session.put("userId", "user-" + UUID.randomUUID());
        session.put("createdAt", System.currentTimeMillis());
        session.put("lastAccessedAt", System.currentTimeMillis());
        session.put("authenticated", true);
        session.put("roles", new String[]{"USER", "READER"});

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("cartItems", 3);
        attributes.put("locale", "en-US");
        attributes.put("theme", "dark");
        session.put("attributes", attributes);

        String base;
        try {
            base = mapper.writeValueAsString(session);
        } catch (Exception e) {
            base = "{}";
        }

        // Pad with a filler field to reach the target size.
        int overhead = base.length() + 12; // ", \"data\":\"\"}" minus closing brace already counted
        int padLen = Math.max(0, targetBytes - overhead);
        StringBuilder sb = new StringBuilder(base.length() + padLen + 16);
        sb.append(base, 0, base.length() - 1); // drop trailing }
        sb.append(",\"data\":\"");
        char[] filler = new char[padLen];
        java.util.Arrays.fill(filler, 'x');
        sb.append(filler);
        sb.append("\"}");

        String json = sb.toString();
        return new Payload(json, json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    public record Payload(String json, int byteLength) {}
}
