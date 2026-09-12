package com.aryandhere.ratelimiter.benchmark;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Renders a {@link BenchmarkReport} as machine-readable CSV: one row per
 * trial, plus one median row per algorithm. Purely tabular — environment and
 * narrative details belong in {@link MarkdownReportWriter} instead.
 */
public final class CsvReportWriter {

    private static final String[] HEADER = {
        "algorithm", "trial", "total_operations", "accepted", "rejected", "elapsed_ms",
        "throughput_ops_per_sec", "min_ms", "mean_ms", "p50_ms", "p95_ms", "p99_ms", "max_ms"
    };

    private CsvReportWriter() {
    }

    public static String render(BenchmarkReport report) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADER)).append('\n');

        for (AlgorithmSummary summary : report.algorithms()) {
            for (TrialResult trial : summary.trials()) {
                appendRow(csv, summary.algorithmName(), String.valueOf(trial.trialIndex() + 1),
                        trial.totalOperations(), trial.accepted(), trial.rejected(),
                        trial.elapsedNanos() / 1_000_000.0, trial.throughputOperationsPerSecond(),
                        trial.latency().minMillis(), trial.latency().meanMillis(), trial.latency().p50Millis(),
                        trial.latency().p95Millis(), trial.latency().p99Millis(), trial.latency().maxMillis());
            }
            MedianSummary median = summary.median();
            appendRow(csv, summary.algorithmName(), "median",
                    Math.round(median.medianAccepted() + median.medianRejected()),
                    median.medianAccepted(), median.medianRejected(),
                    "", median.medianThroughputOperationsPerSecond(),
                    median.medianMinNanos() / 1_000_000.0, median.medianMeanNanos() / 1_000_000.0,
                    median.medianP50Nanos() / 1_000_000.0, median.medianP95Nanos() / 1_000_000.0,
                    median.medianP99Nanos() / 1_000_000.0, median.medianMaxNanos() / 1_000_000.0);
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, String algorithm, String trial, Object... values) {
        StringJoiner row = new StringJoiner(",");
        row.add(algorithm).add(trial);
        for (Object value : values) {
            row.add(format(value));
        }
        csv.append(row).append('\n');
    }

    private static String format(Object value) {
        if (value instanceof Double d) {
            return String.format(Locale.ROOT, "%.3f", d);
        }
        return String.valueOf(value);
    }
}
