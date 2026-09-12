package com.aryandhere.ratelimiter.core;

import com.aryandhere.ratelimiter.time.TimeSource;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window log rate limiter.
 *
 * <p>Each client keeps a log of the timestamps of its recent requests.
 * A request is allowed only if, after discarding timestamps older than the
 * configured window, fewer than {@code maxRequests} remain within the
 * window. This gives an exact rate limit with no boundary effects: unlike
 * {@link FixedWindowRateLimiter}, a client can never exceed {@code
 * maxRequests} in any rolling window of {@code windowSize}, including spans
 * that straddle two fixed-window boundaries.
 *
 * <p><strong>Accuracy vs. memory tradeoff:</strong> the precision comes at
 * the cost of storing one timestamp per accepted request per client for the
 * lifetime of the window. A high-traffic client with a generous limit and a
 * long window keeps a correspondingly large log in memory, whereas fixed
 * window and token bucket limiters use constant space per client regardless
 * of traffic volume.
 */
public final class SlidingWindowLogRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final TimeSource timeSource;
    private final ConcurrentHashMap<String, Deque<Long>> logsByClient = new ConcurrentHashMap<>();

    /**
     * @param maxRequests maximum requests allowed per client within any window; must be positive
     * @param windowSize duration of the rolling window; must be positive
     * @param timeSource source of the current time
     */
    public SlidingWindowLogRateLimiter(int maxRequests, Duration windowSize, TimeSource timeSource) {
        this.maxRequests = Validation.requirePositive(maxRequests, "maxRequests");
        this.windowMillis = Validation.requirePositive(windowSize, "windowSize").toMillis();
        this.timeSource = timeSource;
    }

    @Override
    public RateLimitDecision decide(String clientId) {
        Validation.requireClientId(clientId);
        Deque<Long> log = logsByClient.computeIfAbsent(clientId, id -> new ArrayDeque<>());
        long now = timeSource.currentTimeMillis();
        synchronized (log) {
            long windowStart = now - windowMillis;
            while (!log.isEmpty() && log.peekFirst() <= windowStart) {
                log.pollFirst();
            }
            if (log.size() < maxRequests) {
                log.addLast(now);
                return RateLimitDecision.allow(maxRequests, maxRequests - log.size());
            }
            long oldestTimestamp = log.peekFirst();
            long retryAfterMillis = (oldestTimestamp + windowMillis) - now;
            return RateLimitDecision.reject(maxRequests, RetryAfter.ceilSeconds(retryAfterMillis));
        }
    }
}
