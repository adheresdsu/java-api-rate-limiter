package com.aryandhere.ratelimiter.config;

import com.aryandhere.ratelimiter.core.FixedWindowRateLimiter;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.core.SlidingWindowLogRateLimiter;
import com.aryandhere.ratelimiter.core.TokenBucketRateLimiter;
import com.aryandhere.ratelimiter.time.TimeSource;
import java.time.Duration;

/** Builds the {@link RateLimiter} selected by a {@link ServerConfig}, in one place. */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
    }

    public static RateLimiter create(ServerConfig config, TimeSource timeSource) {
        return switch (config.algorithm()) {
            case FIXED_WINDOW ->
                new FixedWindowRateLimiter(config.limit(), Duration.ofSeconds(config.windowSeconds()), timeSource);
            case SLIDING_WINDOW ->
                new SlidingWindowLogRateLimiter(config.limit(), Duration.ofSeconds(config.windowSeconds()), timeSource);
            case TOKEN_BUCKET -> {
                double refillRatePerSecond = config.refillTokens() / config.refillPeriodSeconds();
                yield new TokenBucketRateLimiter(config.capacity(), refillRatePerSecond, timeSource);
            }
        };
    }
}
