package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.config.AlgorithmType;
import com.aryandhere.ratelimiter.stats.LatencyStats;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarkdownReportWriterTest {

    private BenchmarkReport sampleReport() {
        LatencyStats latency = LatencyStats.of(new long[] {1_000_000L, 2_000_000L, 3_000_000L});
        List<TrialResult> trials = List.of(
                new TrialResult("fixed-window", 0, 10, 5, 5, 1_000_000_000L, latency),
                new TrialResult("fixed-window", 1, 10, 6, 4, 1_000_000_000L, latency),
                new TrialResult("fixed-window", 2, 10, 4, 6, 1_000_000_000L, latency));
        AlgorithmSummary summary = AlgorithmSummary.of("fixed-window", trials);
        EnvironmentInfo env = new EnvironmentInfo("21.0.1", "Test OS", "1.0", "x86_64", 8);
        BenchmarkConfig config = new BenchmarkConfig(2, 10, 2, 0, 3);
        return new BenchmarkReport(Instant.parse("2026-01-01T00:00:00Z"), env, "abc1234", config, List.of(summary));
    }

    @Test
    void includesAllRequiredSections() {
        String markdown = MarkdownReportWriter.render(sampleReport());

        assertTrue(markdown.contains("# Gatekeeper algorithm benchmark"));
        assertTrue(markdown.contains("## Environment"));
        assertTrue(markdown.contains("## Configuration"));
        assertTrue(markdown.contains("## Trial-level results"));
        assertTrue(markdown.contains("## Median summary"));
        assertTrue(markdown.contains("## Interpretation"));
        assertTrue(markdown.contains("## Limitations"));
        assertTrue(markdown.contains("abc1234"));
    }

    @Test
    void reportsUnknownGitCommitWithoutFailing() {
        BenchmarkReport report = sampleReport();
        BenchmarkReport withoutCommit = new BenchmarkReport(
                report.generatedAt(), report.environment(), null, report.config(), report.algorithms());

        String markdown = MarkdownReportWriter.render(withoutCommit);

        assertTrue(markdown.contains("Git commit: unknown"));
    }

    @Test
    void doesNotLeakPersonalPathsOrHostname() {
        BenchmarkConfig config = new BenchmarkConfig(2, 10, 2, 0, 3);
        Map<AlgorithmType, List<TrialResult>> resultsByAlgorithm = new EnumMap<>(AlgorithmType.class);
        LatencyStats latency = LatencyStats.of(new long[] {1_000_000L});
        for (AlgorithmType algorithm : AlgorithmType.values()) {
            resultsByAlgorithm.put(algorithm, List.of(
                    new TrialResult(algorithm.cliName(), 0, 10, 5, 5, 1_000_000_000L, latency),
                    new TrialResult(algorithm.cliName(), 1, 10, 5, 5, 1_000_000_000L, latency),
                    new TrialResult(algorithm.cliName(), 2, 10, 5, 5, 1_000_000_000L, latency)));
        }
        BenchmarkReport report = BenchmarkReport.of(config, resultsByAlgorithm);

        String markdown = MarkdownReportWriter.render(report);
        String csv = CsvReportWriter.render(report);

        String userHome = System.getProperty("user.home");
        String userName = System.getProperty("user.name");
        assertFalse(markdown.contains(userHome), "markdown must not contain the user's home directory");
        assertFalse(markdown.contains(userName), "markdown must not contain the username");
        assertFalse(csv.contains(userHome), "csv must not contain the user's home directory");
        assertFalse(csv.contains(userName), "csv must not contain the username");
    }
}
