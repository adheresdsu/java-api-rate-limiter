package com.aryandhere.ratelimiter.benchmark;

import java.util.List;

/** Every trial result for one algorithm, plus the per-metric median across them. */
public record AlgorithmSummary(String algorithmName, List<TrialResult> trials, MedianSummary median) {

    public static AlgorithmSummary of(String algorithmName, List<TrialResult> trials) {
        return new AlgorithmSummary(algorithmName, List.copyOf(trials), MedianSummary.of(trials));
    }
}
