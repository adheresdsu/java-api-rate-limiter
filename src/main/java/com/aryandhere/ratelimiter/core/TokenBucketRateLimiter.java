package com.aryandhere.ratelimiter.core;

import com.aryandhere.ratelimiter.time.TimeSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter.
 *
 * <p>Each client owns a bucket that holds up to {@code capacity} tokens.
 * Every accepted request consumes one token. Tokens are refilled lazily,
 * added back at {@code refillRatePerSecond} based on the elapsed time since
 * the bucket was last touched, and the bucket never holds more than {@code
 * capacity} tokens.
 *
 * <p>This lets a client burst up to {@code capacity} requests instantly
 * (useful for clients that are idle most of the time but occasionally send
 * a batch), while the refill rate caps its sustained long-term throughput
 * at {@code refillRatePerSecond} requests per second.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final double capacity;
    private final double refillRatePerSecond;
    private final TimeSource timeSource;
    private final ConcurrentHashMap<String, Bucket> bucketsByClient = new ConcurrentHashMap<>();

    /**
     * @param capacity maximum number of tokens (and therefore the maximum burst size); must be positive
     * @param refillRatePerSecond tokens added back per second; must be positive
     * @param timeSource source of the current time
     */
    public TokenBucketRateLimiter(long capacity, double refillRatePerSecond, TimeSource timeSource) {
        this.capacity = Validation.requirePositive(capacity, "capacity");
        this.refillRatePerSecond = Validation.requirePositive(refillRatePerSecond, "refillRatePerSecond");
        this.timeSource = timeSource;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        Validation.requireClientId(clientId);
        Bucket bucket = bucketsByClient.computeIfAbsent(clientId, id -> new Bucket(capacity, timeSource.currentTimeMillis()));
        return bucket.tryConsume(timeSource.currentTimeMillis());
    }

    /** Per-client mutable state, synchronized independently of every other client. */
    private final class Bucket {
        private double tokens;
        private long lastRefillMillis;

        Bucket(double initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMillis = now;
        }

        synchronized boolean tryConsume(long now) {
            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill(long now) {
            long elapsedMillis = now - lastRefillMillis;
            if (elapsedMillis <= 0) {
                return;
            }
            double refillAmount = (elapsedMillis / 1000.0) * refillRatePerSecond;
            tokens = Math.min(capacity, tokens + refillAmount);
            lastRefillMillis = now;
        }
    }
}
