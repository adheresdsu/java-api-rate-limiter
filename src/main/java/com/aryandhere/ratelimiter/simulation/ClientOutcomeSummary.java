package com.aryandhere.ratelimiter.simulation;

/**
 * Per-client outcome counts.
 *
 * <p>This reports <em>client-distribution consistency</em> — whether
 * identically configured simulated clients happened to receive similar
 * outcomes in this run — not demographic or social fairness.
 */
public record ClientOutcomeSummary(String clientId, long accepted, long rejected, long otherErrors) {

    public long total() {
        return accepted + rejected + otherErrors;
    }
}
