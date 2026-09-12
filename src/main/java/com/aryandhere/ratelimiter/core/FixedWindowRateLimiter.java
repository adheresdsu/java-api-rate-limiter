package com.aryandhere.ratelimiter.core;

import com.aryandhere.ratelimiter.time.TimeSource;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed window rate limiter.
 *
 * <p>Each client is allowed at most {@code maxRequests} requests inside a
 * window of {@code windowSize}. A client's count resets the moment it makes
 * a request in a new window; the window boundaries are anchored to the
 * client's first request rather than to wall-clock intervals of the epoch.
 *
 * <p><strong>Burst-at-boundary weakness:</strong> because the count resets
 * abruptly at each window edge, a client can send {@code maxRequests}
 * requests at the very end of one window and another {@code maxRequests} at
 * the very start of the next. Those two bursts can land within a tiny
 * fraction of the configured window size, letting the client briefly send
 * up to twice its intended rate. {@link SlidingWindowLogRateLimiter} avoids
 * this by tracking exact request timestamps instead of resetting counts in
 * bulk.
 */
public final class FixedWindowRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final TimeSource timeSource;
    private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

    /**
     * @param maxRequests maximum requests allowed per client per window; must be positive
     * @param windowSize duration of each window; must be positive
     * @param timeSource source of the current time
     */
    public FixedWindowRateLimiter(int maxRequests, Duration windowSize, TimeSource timeSource) {
        this.maxRequests = Validation.requirePositive(maxRequests, "maxRequests");
        this.windowMillis = Validation.requirePositive(windowSize, "windowSize").toMillis();
        this.timeSource = timeSource;
    }

    @Override
    public RateLimitDecision decide(String clientId) {
        Validation.requireClientId(clientId);
        Window window = windowsByClient.computeIfAbsent(clientId, id -> new Window(timeSource.currentTimeMillis()));
        return window.decide(timeSource.currentTimeMillis());
    }

    /** Per-client mutable state, synchronized independently of every other client. */
    private final class Window {
        private long windowStart;
        private int count;

        Window(long windowStart) {
            this.windowStart = windowStart;
            this.count = 0;
        }

        synchronized RateLimitDecision decide(long now) {
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            if (count < maxRequests) {
                count++;
                return RateLimitDecision.allow(maxRequests, maxRequests - count);
            }
            long remainingWindowMillis = windowMillis - (now - windowStart);
            return RateLimitDecision.reject(maxRequests, RetryAfter.ceilSeconds(remainingWindowMillis));
        }
    }
}
