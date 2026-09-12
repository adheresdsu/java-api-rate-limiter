package com.aryandhere.ratelimiter.core;

/**
 * Decides whether a request from a given client should be allowed.
 *
 * <p>Implementations must be safe for concurrent use: multiple threads may
 * call {@link #tryAcquire(String)} for the same or different client
 * identifiers at the same time.
 */
public interface RateLimiter {

    /**
     * Attempts to record a request for the given client and determines
     * whether it should proceed.
     *
     * @param clientId identifies the caller the limit is tracked against
     *     (for example an API key or IP address); must not be {@code null}
     *     or blank
     * @return {@code true} if the request is allowed and has been counted
     *     against the client's limit, {@code false} if the client has
     *     exceeded its limit and the request must be rejected
     * @throws IllegalArgumentException if {@code clientId} is {@code null}
     *     or blank
     */
    boolean tryAcquire(String clientId);
}
