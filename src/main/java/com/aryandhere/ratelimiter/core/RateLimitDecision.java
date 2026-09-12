package com.aryandhere.ratelimiter.core;

import java.time.Duration;

/**
 * The outcome of a single {@link RateLimiter#decide(String)} call.
 *
 * @param allowed whether the request is allowed
 * @param limit the algorithm's configured ceiling for the client (for
 *     example {@code maxRequests} or bucket {@code capacity}); always
 *     non-negative
 * @param remaining an estimate of how many further requests the client
 *     could make right now without being rejected; never negative, and
 *     {@code 0} whenever {@code allowed} is {@code false}
 * @param retryAfter how long the client should wait before its next
 *     request is likely to be allowed, or {@code null} when the algorithm
 *     has no such estimate to offer (in particular, always {@code null}
 *     when {@code allowed} is {@code true}). Never negative when present.
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, Duration retryAfter) {

    public RateLimitDecision {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative, got " + limit);
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative, got " + remaining);
        }
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative, got " + retryAfter);
        }
    }

    /** Creates an "allowed" decision with no retry-after value. */
    public static RateLimitDecision allow(long limit, long remaining) {
        return new RateLimitDecision(true, limit, remaining, null);
    }

    /** Creates a "rejected" decision. {@code remaining} is always reported as 0. */
    public static RateLimitDecision reject(long limit, Duration retryAfter) {
        return new RateLimitDecision(false, limit, 0, retryAfter);
    }
}
