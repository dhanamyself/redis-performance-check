package com.azure.redisperf;

import com.azure.redisperf.service.SessionPayloadFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionPayloadFactoryTest {

    private final SessionPayloadFactory factory = new SessionPayloadFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsValidSessionJson() throws Exception {
        SessionPayloadFactory.Payload p = factory.build("sess-1", 1024);
        JsonNode node = mapper.readTree(p.json());
        assertEquals("sess-1", node.get("sessionId").asText());
        assertTrue(node.has("attributes"));
        assertTrue(node.get("authenticated").asBoolean());
    }

    @Test
    void hitsApproximateTargetSizeAcrossRange() {
        int[] targets = {1024, 10 * 1024, 100 * 1024, 1024 * 1024};
        for (int target : targets) {
            int actual = factory.build("k", target).byteLength();
            // Within 2% (or 64 bytes) of the requested size.
            int tolerance = Math.max(64, target / 50);
            assertTrue(Math.abs(actual - target) <= tolerance,
                    "size " + target + " produced " + actual);
        }
    }

    @Test
    void neverProducesNegativePadding() {
        // Target smaller than the base JSON must still yield valid JSON.
        SessionPayloadFactory.Payload p = factory.build("k", 16);
        assertNotNull(p.json());
        assertTrue(p.byteLength() > 0);
    }
}
