package com.aryandhere.ratelimiter.core;

/**
 * Decides whether a request from a given client should be allowed.
 *
 * <p>Implementations must be safe for concurrent use: multiple threads may
 * call {@link #decide(String)} for the same or different client
 * identifiers at the same time.
 */
public interface RateLimiter {

    /**
     * Attempts to record a request for the given client and returns the
     * full decision, including remaining capacity and (when rejected) a
     * retry-after estimate.
     *
     * @param clientId identifies the caller the limit is tracked against
     *     (for example an API key or IP address); must not be {@code null}
     *     or blank
     * @return the decision; never {@code null}
     * @throws IllegalArgumentException if {@code clientId} is {@code null}
     *     or blank
     */
    RateLimitDecision decide(String clientId);

    /**
     * Convenience form of {@link #decide(String)} for callers that only
     * need the allow/reject outcome.
     *
     * @param clientId see {@link #decide(String)}
     * @return {@code true} if the request is allowed and has been counted
     *     against the client's limit, {@code false} if the client has
     *     exceeded its limit and the request must be rejected
     * @throws IllegalArgumentException if {@code clientId} is {@code null}
     *     or blank
     */
    default boolean tryAcquire(String clientId) {
        return decide(clientId).allowed();
    }
}
