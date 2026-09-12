package com.aryandhere.ratelimiter.benchmark;

import com.aryandhere.ratelimiter.config.AlgorithmType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Command-line entry point for the direct algorithm benchmark. */
public final class AlgorithmBenchmarkApplication {

    private static final Path REPORTS_DIRECTORY = Path.of("reports", "benchmarks");

    private AlgorithmBenchmarkApplication() {
    }

    public static void main(String[] args) {
        BenchmarkConfig config = parseConfigOrExit(args);

        System.out.println("Clients: " + config.clients() + ", operations/client: " + config.operationsPerClient()
                + ", concurrency: " + config.concurrency());
        System.out.println("Warmup operations per trial: " + config.warmupOperations()
                + ", trials: " + config.trials());
        System.out.println("Running benchmark...");

        BenchmarkRunner runner = new BenchmarkRunner();
        Map<AlgorithmType, List<TrialResult>> resultsByAlgorithm;
        try {
            resultsByAlgorithm = runner.run(config);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error: benchmark was interrupted");
            System.exit(1);
            return;
        }

        BenchmarkReport report = BenchmarkReport.of(config, resultsByAlgorithm);
        printConsoleSummary(report);
        writeReports(report);
    }

    private static BenchmarkConfig parseConfigOrExit(String[] args) {
        try {
            return BenchmarkArgsParser.parse(args);
        } catch (BenchmarkConfigException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.print(BenchmarkArgsParser.usage());
            System.exit(1);
            throw new AssertionError("unreachable");
        }
    }

    private static void printConsoleSummary(BenchmarkReport report) {
        System.out.println();
        System.out.println("=== Median summary (per metric, across trials) ===");
        for (AlgorithmSummary summary : report.algorithms()) {
            MedianSummary median = summary.median();
            System.out.printf(Locale.ROOT,
                    "%-15s accepted=%.1f rejected=%.1f throughput=%.1f ops/s p50=%.3fms p95=%.3fms p99=%.3fms%n",
                    summary.algorithmName(), median.medianAccepted(), median.medianRejected(),
                    median.medianThroughputOperationsPerSecond(), median.medianP50Nanos() / 1_000_000.0,
                    median.medianP95Nanos() / 1_000_000.0, median.medianP99Nanos() / 1_000_000.0);
        }
    }

    private static void writeReports(BenchmarkReport report) {
        try {
            Files.createDirectories(REPORTS_DIRECTORY);
            Path csvPath = ReportPaths.csvPath(REPORTS_DIRECTORY, report.generatedAt());
            Path markdownPath = ReportPaths.markdownPath(REPORTS_DIRECTORY, report.generatedAt());
            Files.writeString(csvPath, CsvReportWriter.render(report), StandardCharsets.UTF_8);
            Files.writeString(markdownPath, MarkdownReportWriter.render(report), StandardCharsets.UTF_8);
            System.out.println();
            System.out.println("Reports written:");
            System.out.println("  " + csvPath);
            System.out.println("  " + markdownPath);
        } catch (IOException e) {
            System.err.println("Warning: failed to write report files: " + e.getMessage());
        }
    }
}
