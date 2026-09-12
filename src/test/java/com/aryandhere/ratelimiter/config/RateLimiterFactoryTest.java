package com.aryandhere.ratelimiter.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.core.FixedWindowRateLimiter;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.core.SlidingWindowLogRateLimiter;
import com.aryandhere.ratelimiter.core.TokenBucketRateLimiter;
import com.aryandhere.ratelimiter.time.ManualTimeSource;
import org.junit.jupiter.api.Test;

class RateLimiterFactoryTest {

    @Test
    void buildsFixedWindowLimiter() {
        ServerConfig config = new ServerConfig(8080, AlgorithmType.FIXED_WINDOW, 2, 60, 20, 10, 1, 4);

        RateLimiter limiter = RateLimiterFactory.create(config, new ManualTimeSource(0));

        assertInstanceOf(FixedWindowRateLimiter.class, limiter);
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void buildsSlidingWindowLimiter() {
        ServerConfig config = new ServerConfig(8080, AlgorithmType.SLIDING_WINDOW, 2, 60, 20, 10, 1, 4);

        RateLimiter limiter = RateLimiterFactory.create(config, new ManualTimeSource(0));

        assertInstanceOf(SlidingWindowLogRateLimiter.class, limiter);
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void buildsTokenBucketLimiter() {
        ServerConfig config = new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 2, 60, 2, 10, 1, 4);

        RateLimiter limiter = RateLimiterFactory.create(config, new ManualTimeSource(0));

        assertInstanceOf(TokenBucketRateLimiter.class, limiter);
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }
}
