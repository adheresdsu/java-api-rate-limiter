package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.stats.LatencyStats;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReportWriterTest {

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
    void includesHeaderRow() {
        String csv = CsvReportWriter.render(sampleReport());

        String firstLine = csv.lines().findFirst().orElseThrow();
        assertEquals("algorithm,trial,total_operations,accepted,rejected,elapsed_ms,throughput_ops_per_sec,"
                + "min_ms,mean_ms,p50_ms,p95_ms,p99_ms,max_ms", firstLine);
    }

    @Test
    void includesOneRowPerTrialPlusOneMedianRow() {
        String csv = CsvReportWriter.render(sampleReport());

        long dataLines = csv.lines().count() - 1; // minus header
        assertEquals(4, dataLines); // 3 trials + 1 median
    }

    @Test
    void medianRowIsLabeled() {
        String csv = CsvReportWriter.render(sampleReport());

        assertTrue(csv.lines().anyMatch(line -> line.startsWith("fixed-window,median,")));
    }

    @Test
    void everyDataRowHasTheSameColumnCountAsTheHeader() {
        String csv = CsvReportWriter.render(sampleReport());
        List<String> lines = csv.lines().toList();
        int headerColumns = lines.get(0).split(",", -1).length;

        for (int i = 1; i < lines.size(); i++) {
            assertEquals(headerColumns, lines.get(i).split(",", -1).length, "row " + i + " column count mismatch");
        }
    }
}
