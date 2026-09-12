package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aryandhere.ratelimiter.stats.LatencyStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class MedianSummaryTest {

    private TrialResult trial(int trialIndex, long accepted, long rejected, long elapsedNanos, long p50Nanos) {
        // A single-value LatencyStats makes every statistic equal to p50Nanos, keeping the fixture simple.
        LatencyStats latency = LatencyStats.of(new long[] {p50Nanos});
        return new TrialResult("fixed-window", trialIndex, accepted + rejected, accepted, rejected, elapsedNanos,
                latency);
    }

    @Test
    void oddTrialCountTakesTheMiddleValue() {
        List<TrialResult> trials = List.of(
                trial(0, 10, 5, 1_000_000_000L, 100),
                trial(1, 20, 5, 1_000_000_000L, 300),
                trial(2, 30, 5, 1_000_000_000L, 200));

        MedianSummary median = MedianSummary.of(trials);

        assertEquals(200, median.medianP50Nanos());
        assertEquals(20.0, median.medianAccepted());
        assertEquals(5.0, median.medianRejected());
    }

    @Test
    void evenTrialCountAveragesTheTwoMiddleValues() {
        List<TrialResult> trials = List.of(
                trial(0, 10, 1, 1_000_000_000L, 100),
                trial(1, 20, 2, 1_000_000_000L, 200),
                trial(2, 30, 3, 1_000_000_000L, 300),
                trial(3, 40, 4, 1_000_000_000L, 400));

        MedianSummary median = MedianSummary.of(trials);

        assertEquals(250.0, median.medianP50Nanos()); // (200 + 300) / 2, truncated to long -> 250
        assertEquals(25.0, median.medianAccepted()); // (20 + 30) / 2
        assertEquals(2.5, median.medianRejected()); // (2 + 3) / 2
    }

    @Test
    void throughputMedianReflectsMiddleTrial() {
        // Same total operations, different elapsed times -> different throughput per trial.
        List<TrialResult> trials = List.of(
                trial(0, 100, 0, 2_000_000_000L, 100), // 50 ops/s
                trial(1, 100, 0, 500_000_000L, 100),   // 200 ops/s
                trial(2, 100, 0, 1_000_000_000L, 100)); // 100 ops/s

        MedianSummary median = MedianSummary.of(trials);

        assertEquals(100.0, median.medianThroughputOperationsPerSecond());
    }
}
