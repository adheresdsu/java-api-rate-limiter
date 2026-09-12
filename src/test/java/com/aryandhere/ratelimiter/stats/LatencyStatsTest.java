package com.aryandhere.ratelimiter.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatencyStatsTest {

    @Test
    void emptySampleReportsAllZeros() {
        LatencyStats stats = LatencyStats.of(new long[0]);

        assertEquals(0, stats.count());
        assertEquals(0, stats.minNanos());
        assertEquals(0, stats.meanNanos());
        assertEquals(0, stats.p50Nanos());
        assertEquals(0, stats.p95Nanos());
        assertEquals(0, stats.p99Nanos());
        assertEquals(0, stats.maxNanos());
    }

    @Test
    void singleSampleEqualsEveryStatistic() {
        LatencyStats stats = LatencyStats.of(new long[] {1_000_000L});

        assertEquals(1, stats.count());
        assertEquals(1_000_000L, stats.minNanos());
        assertEquals(1_000_000.0, stats.meanNanos());
        assertEquals(1_000_000L, stats.p50Nanos());
        assertEquals(1_000_000L, stats.p95Nanos());
        assertEquals(1_000_000L, stats.p99Nanos());
        assertEquals(1_000_000L, stats.maxNanos());
    }

    @Test
    void computesPercentilesOnKnownDatasetOfTen() {
        // 1..10 (in an arbitrary nanosecond unit), nearest-rank method.
        long[] samples = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        LatencyStats stats = LatencyStats.of(samples);

        assertEquals(1, stats.minNanos());
        assertEquals(10, stats.maxNanos());
        assertEquals(5.5, stats.meanNanos());
        // rank = ceil(0.50 * 10) = 5 -> index 4 -> value 5
        assertEquals(5, stats.p50Nanos());
        // rank = ceil(0.95 * 10) = 10 -> index 9 -> value 10
        assertEquals(10, stats.p95Nanos());
        // rank = ceil(0.99 * 10) = 10 -> index 9 -> value 10
        assertEquals(10, stats.p99Nanos());
    }

    @Test
    void computesPercentilesOnKnownDatasetOfHundred() {
        long[] samples = new long[100];
        for (int i = 0; i < 100; i++) {
            samples[i] = i + 1; // 1..100
        }

        LatencyStats stats = LatencyStats.of(samples);

        // rank = ceil(0.50 * 100) = 50 -> index 49 -> value 50
        assertEquals(50, stats.p50Nanos());
        // rank = ceil(0.95 * 100) = 95 -> index 94 -> value 95
        assertEquals(95, stats.p95Nanos());
        // rank = ceil(0.99 * 100) = 99 -> index 98 -> value 99
        assertEquals(99, stats.p99Nanos());
    }

    @Test
    void latencyOrderingHoldsOnUnsortedInput() {
        long[] samples = {50, 10, 30, 20, 40, 100, 90, 80, 70, 60};

        LatencyStats stats = LatencyStats.of(samples);

        assertTrue(stats.minNanos() <= stats.p50Nanos());
        assertTrue(stats.p50Nanos() <= stats.p95Nanos());
        assertTrue(stats.p95Nanos() <= stats.p99Nanos());
        assertTrue(stats.p99Nanos() <= stats.maxNanos());
    }

    @Test
    void millisConversionsDivideByOneMillion() {
        LatencyStats stats = LatencyStats.of(new long[] {2_000_000L});

        assertEquals(2.0, stats.minMillis());
        assertEquals(2.0, stats.meanMillis());
        assertEquals(2.0, stats.maxMillis());
    }
}
