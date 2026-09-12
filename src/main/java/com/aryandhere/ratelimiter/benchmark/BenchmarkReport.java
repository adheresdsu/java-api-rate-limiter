package com.aryandhere.ratelimiter.benchmark;

import com.aryandhere.ratelimiter.config.AlgorithmType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything needed to render a complete, reproducible benchmark report.
 *
 * @param generatedAt when the benchmark was run
 * @param environment non-identifying machine/JVM facts
 * @param gitCommit short commit hash, or {@code null} if unavailable
 * @param config the exact configuration used
 * @param algorithms one summary per algorithm, in a fixed order
 */
public record BenchmarkReport(
        Instant generatedAt, EnvironmentInfo environment, String gitCommit, BenchmarkConfig config,
        List<AlgorithmSummary> algorithms) {

    public static BenchmarkReport of(BenchmarkConfig config, Map<AlgorithmType, List<TrialResult>> resultsByAlgorithm) {
        List<AlgorithmSummary> summaries = new ArrayList<>();
        for (Map.Entry<AlgorithmType, List<TrialResult>> entry : resultsByAlgorithm.entrySet()) {
            summaries.add(AlgorithmSummary.of(entry.getKey().cliName(), entry.getValue()));
        }
        return new BenchmarkReport(
                Instant.now(), EnvironmentInfo.capture(), GitInfo.currentShortCommit().orElse(null), config,
                summaries);
    }
}
