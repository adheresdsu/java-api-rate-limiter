package com.aryandhere.ratelimiter.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * The median, computed independently per metric, across a set of {@link
 * TrialResult}s for one algorithm.
 *
 * <p>Taking the median of each metric separately (rather than picking one
 * "median trial") is standard practice for small benchmark trial counts: it
 * reduces sensitivity to a single outlier trial (e.g. one disrupted by GC or
 * OS scheduling) without needing many trials.
 *
 * <p>Median of an even-sized set is the average of the two middle values.
 */
public record MedianSummary(
        double medianThroughputOperationsPerSecond,
        long medianMinNanos,
        double medianMeanNanos,
        long medianP50Nanos,
        long medianP95Nanos,
        long medianP99Nanos,
        long medianMaxNanos,
        double medianAccepted,
        double medianRejected) {

    static MedianSummary of(List<TrialResult> trials) {
        double[] throughput = trials.stream().mapToDouble(TrialResult::throughputOperationsPerSecond).toArray();
        long[] min = trials.stream().mapToLong(t -> t.latency().minNanos()).toArray();
        double[] mean = trials.stream().mapToDouble(t -> t.latency().meanNanos()).toArray();
        long[] p50 = trials.stream().mapToLong(t -> t.latency().p50Nanos()).toArray();
        long[] p95 = trials.stream().mapToLong(t -> t.latency().p95Nanos()).toArray();
        long[] p99 = trials.stream().mapToLong(t -> t.latency().p99Nanos()).toArray();
        long[] max = trials.stream().mapToLong(t -> t.latency().maxNanos()).toArray();
        double[] accepted = trials.stream().mapToDouble(TrialResult::accepted).toArray();
        double[] rejected = trials.stream().mapToDouble(TrialResult::rejected).toArray();

        return new MedianSummary(
                medianOf(throughput),
                (long) medianOf(min),
                medianOf(mean),
                (long) medianOf(p50),
                (long) medianOf(p95),
                (long) medianOf(p99),
                (long) medianOf(max),
                medianOf(accepted),
                medianOf(rejected));
    }

    private static double medianOf(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 1) {
            return sorted[n / 2];
        }
        return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static double medianOf(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 1) {
            return sorted[n / 2];
        }
        return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
