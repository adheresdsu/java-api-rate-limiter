package com.aryandhere.ratelimiter.benchmark;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a {@link BenchmarkReport} as a human-readable Markdown document:
 * environment, exact configuration, trial-level results, medians, and a
 * plain-language interpretation with stated limitations.
 */
public final class MarkdownReportWriter {

    private MarkdownReportWriter() {
    }

    public static String render(BenchmarkReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Gatekeeper algorithm benchmark\n\n");
        md.append("Generated: ").append(DateTimeFormatter.ISO_INSTANT.format(report.generatedAt())).append(" (UTC)\n\n");
        md.append("Git commit: ").append(report.gitCommit() == null ? "unknown" : report.gitCommit()).append("\n\n");

        appendEnvironment(md, report);
        appendConfig(md, report);
        appendTrials(md, report);
        appendMedianSummary(md, report);
        appendInterpretation(md, report);
        appendLimitations(md);
        return md.toString();
    }

    private static void appendEnvironment(StringBuilder md, BenchmarkReport report) {
        EnvironmentInfo env = report.environment();
        md.append("## Environment\n\n");
        md.append("- Java: ").append(env.javaVersion()).append('\n');
        md.append("- OS: ").append(env.osName()).append(' ').append(env.osVersion())
                .append(" (").append(env.osArch()).append(")\n");
        md.append("- Available processors: ").append(env.processors()).append('\n');
        md.append('\n');
    }

    private static void appendConfig(StringBuilder md, BenchmarkReport report) {
        BenchmarkConfig config = report.config();
        md.append("## Configuration\n\n");
        md.append("- Clients: ").append(config.clients()).append('\n');
        md.append("- Operations per client, per trial: ").append(config.operationsPerClient()).append('\n');
        md.append("- Concurrency (worker threads): ").append(config.concurrency()).append('\n');
        md.append("- Warmup operations (per trial, per algorithm, discarded): ")
                .append(config.warmupOperations()).append('\n');
        md.append("- Trials per algorithm: ").append(config.trials()).append('\n');
        md.append("- Total measured operations per algorithm: ")
                .append(config.totalMeasuredOperations() * config.trials()).append('\n');
        md.append('\n');
    }

    private static void appendTrials(StringBuilder md, BenchmarkReport report) {
        md.append("## Trial-level results\n\n");
        md.append("| Algorithm | Trial | Accepted | Rejected | Throughput (ops/s) | "
                + "p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (AlgorithmSummary summary : report.algorithms()) {
            for (TrialResult trial : summary.trials()) {
                md.append("| ").append(summary.algorithmName())
                        .append(" | ").append(trial.trialIndex() + 1)
                        .append(" | ").append(trial.accepted())
                        .append(" | ").append(trial.rejected())
                        .append(" | ").append(fmt(trial.throughputOperationsPerSecond()))
                        .append(" | ").append(fmt(trial.latency().p50Millis()))
                        .append(" | ").append(fmt(trial.latency().p95Millis()))
                        .append(" | ").append(fmt(trial.latency().p99Millis()))
                        .append(" | ").append(fmt(trial.latency().maxMillis()))
                        .append(" |\n");
            }
        }
        md.append('\n');
    }

    private static void appendMedianSummary(StringBuilder md, BenchmarkReport report) {
        md.append("## Median summary (per metric, across trials)\n\n");
        md.append("| Algorithm | Median accepted | Median rejected | Median throughput (ops/s) | "
                + "Median p50 (ms) | Median p95 (ms) | Median p99 (ms) | Median max (ms) |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (AlgorithmSummary summary : report.algorithms()) {
            MedianSummary median = summary.median();
            md.append("| ").append(summary.algorithmName())
                    .append(" | ").append(fmt(median.medianAccepted()))
                    .append(" | ").append(fmt(median.medianRejected()))
                    .append(" | ").append(fmt(median.medianThroughputOperationsPerSecond()))
                    .append(" | ").append(fmt(median.medianP50Nanos() / 1_000_000.0))
                    .append(" | ").append(fmt(median.medianP95Nanos() / 1_000_000.0))
                    .append(" | ").append(fmt(median.medianP99Nanos() / 1_000_000.0))
                    .append(" | ").append(fmt(median.medianMaxNanos() / 1_000_000.0))
                    .append(" |\n");
        }
        md.append('\n');
    }

    private static void appendInterpretation(StringBuilder md, BenchmarkReport report) {
        md.append("## Interpretation\n\n");
        md.append("These numbers describe the cost of calling each `RateLimiter` "
                + "implementation's `decide()` method directly, on this one machine, "
                + "under this one configuration. Higher median throughput and lower "
                + "median latency percentiles are better within this run; they are not "
                + "a claim about behavior on other hardware, JVMs, or workloads. "
                + "Accepted/rejected counts are expected to land near "
                + "`operationsPerClient / 2` per client by construction (see the "
                + "benchmark's synthetic limiter configuration) and mainly confirm that "
                + "every algorithm's accept and reject paths were both exercised.\n\n");
    }

    private static void appendLimitations(StringBuilder md) {
        md.append("## Limitations\n\n");
        md.append("- This is a practical, single-machine project benchmark, not a "
                + "scientifically controlled or statistically rigorous performance study.\n");
        md.append("- JIT warm-up, GC pauses, and OS scheduling can all affect individual "
                + "trials; the median-per-metric summary reduces but does not eliminate this.\n");
        md.append("- The synthetic limiter configuration (roughly half the calls accepted) "
                + "is chosen to exercise both code paths, not to represent a realistic "
                + "production rate limit.\n");
        md.append("- Results were not compared across multiple machines or repeated runs "
                + "over time, and should not be read as a universal ranking of the three "
                + "algorithms.\n");
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
