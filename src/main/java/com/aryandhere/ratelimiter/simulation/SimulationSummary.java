package com.aryandhere.ratelimiter.simulation;

import com.aryandhere.ratelimiter.stats.LatencyStats;
import java.util.List;

/**
 * Full result of one traffic simulation run.
 *
 * <p>{@code warmupRequestsCompleted} is reported for context only; it is
 * excluded from every other field, all of which describe the measured
 * phase.
 *
 * @param warmupRequestsCompleted warmup requests completed before measurement began
 * @param allowed count of HTTP 200 responses
 * @param rateLimited count of HTTP 429 responses
 * @param clientErrors count of other 4xx responses
 * @param serverErrors count of 5xx responses
 * @param transportErrors count of timeouts or connection failures
 * @param elapsedNanos wall time of the measured phase
 * @param latency latency distribution across every measured request, regardless of outcome
 * @param perClient outcome counts for each simulated client, in client order
 * @param minAcceptedAcrossClients smallest {@code accepted} count across all clients
 * @param maxAcceptedAcrossClients largest {@code accepted} count across all clients
 * @param consistentClientDistribution {@code true} when the spread between
 *     {@code minAcceptedAcrossClients} and {@code maxAcceptedAcrossClients}
 *     is within 10% of the mean accepted count (or at most 1 request) —
 *     a simple heuristic, not a statistical guarantee
 */
public record SimulationSummary(
        long warmupRequestsCompleted,
        long allowed,
        long rateLimited,
        long clientErrors,
        long serverErrors,
        long transportErrors,
        long elapsedNanos,
        LatencyStats latency,
        List<ClientOutcomeSummary> perClient,
        long minAcceptedAcrossClients,
        long maxAcceptedAcrossClients,
        boolean consistentClientDistribution) {

    public long totalMeasuredRequests() {
        return allowed + rateLimited + clientErrors + serverErrors + transportErrors;
    }

    public double throughputRequestsPerSecond() {
        if (elapsedNanos <= 0) {
            return 0;
        }
        return totalMeasuredRequests() / (elapsedNanos / 1_000_000_000.0);
    }
}
