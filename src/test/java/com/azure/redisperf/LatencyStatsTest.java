package com.azure.redisperf;

import com.azure.redisperf.model.LatencyStats;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatencyStatsTest {

    @Test
    void computesPercentilesInMillisFromMicroValues() {
        // Record 1..1000 ms (as microseconds) — 1000 values.
        Histogram h = new Histogram(1, 120_000_000L, 3);
        for (int ms = 1; ms <= 1000; ms++) {
            h.recordValue(ms * 1000L);
        }
        LatencyStats s = LatencyStats.fromHistogram(h);

        assertEquals(1000, s.getCount());
        assertEquals(1.0, s.getMinMs(), 0.5);
        assertEquals(1000.0, s.getMaxMs(), 5.0);
        assertEquals(500.0, s.getP50Ms(), 5.0);
        assertEquals(990.0, s.getP99Ms(), 5.0);
        assertTrue(s.getP99Ms() >= s.getP95Ms());
        assertTrue(s.getP95Ms() >= s.getP50Ms());
    }

    @Test
    void emptyHistogramYieldsZeros() {
        LatencyStats s = LatencyStats.fromHistogram(new Histogram(1, 120_000_000L, 3));
        assertEquals(0, s.getCount());
        assertEquals(0.0, s.getP99Ms(), 0.0);
    }
}
