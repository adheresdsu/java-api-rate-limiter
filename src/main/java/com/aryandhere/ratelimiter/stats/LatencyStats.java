package com.aryandhere.ratelimiter.stats;

import java.util.Arrays;

/**
 * Latency distribution computed from a set of individual measurements.
 *
 * <p>Values are kept internally in nanoseconds, as measured by {@link
 * System#nanoTime()}; callers convert to milliseconds only for display.
 *
 * <p><strong>Percentile method:</strong> samples are sorted ascending, and
 * percentile {@code p} (0-100) is taken using the nearest-rank method:
 * {@code rank = ceil(p / 100.0 * count)}, clamped to at least 1, and the
 * value at zero-based index {@code rank - 1} is returned. This is a common,
 * simple definition (no interpolation between samples) and is stated here
 * so results can be reproduced from raw data.
 *
 * <p>With zero samples every statistic is reported as {@code 0}; with one
 * sample every statistic equals that sample.
 */
public record LatencyStats(
        long count, long minNanos, double meanNanos, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {

    private static final LatencyStats EMPTY = new LatencyStats(0, 0, 0, 0, 0, 0, 0);

    public static LatencyStats of(long[] latenciesNanos) {
        if (latenciesNanos.length == 0) {
            return EMPTY;
        }
        long[] sorted = latenciesNanos.clone();
        Arrays.sort(sorted);

        long sum = 0;
        for (long value : sorted) {
            sum += value;
        }
        double mean = sum / (double) sorted.length;

        return new LatencyStats(
                sorted.length,
                sorted[0],
                mean,
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted[sorted.length - 1]);
    }

    private static long percentile(long[] sortedAscending, int p) {
        int rank = (int) Math.ceil(p / 100.0 * sortedAscending.length);
        int index = Math.clamp(rank - 1, 0, sortedAscending.length - 1);
        return sortedAscending[index];
    }

    public double minMillis() {
        return minNanos / 1_000_000.0;
    }

    public double meanMillis() {
        return meanNanos / 1_000_000.0;
    }

    public double p50Millis() {
        return p50Nanos / 1_000_000.0;
    }

    public double p95Millis() {
        return p95Nanos / 1_000_000.0;
    }

    public double p99Millis() {
        return p99Nanos / 1_000_000.0;
    }

    public double maxMillis() {
        return maxNanos / 1_000_000.0;
    }
}
