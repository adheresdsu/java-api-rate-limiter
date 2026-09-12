package com.aryandhere.ratelimiter.benchmark;

import com.aryandhere.ratelimiter.stats.LatencyStats;

/**
 * The measured outcome of one trial for one algorithm.
 *
 * @param algorithmName the algorithm's CLI name (e.g. {@code token-bucket})
 * @param trialIndex 0-based trial number
 * @param totalOperations {@code accepted + rejected}
 * @param accepted number of {@code decide()} calls that were allowed
 * @param rejected number of {@code decide()} calls that were rejected
 * @param elapsedNanos wall time of the measured phase, excluding warmup
 * @param latency per-call latency distribution for this trial
 */
public record TrialResult(
        String algorithmName,
        int trialIndex,
        long totalOperations,
        long accepted,
        long rejected,
        long elapsedNanos,
        LatencyStats latency) {

    public double throughputOperationsPerSecond() {
        if (elapsedNanos <= 0) {
            return 0;
        }
        return totalOperations / (elapsedNanos / 1_000_000_000.0);
    }
}
