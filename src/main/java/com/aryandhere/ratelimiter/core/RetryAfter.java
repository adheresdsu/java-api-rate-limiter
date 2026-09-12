package com.aryandhere.ratelimiter.core;

import java.time.Duration;

/**
 * Converts a wait time to whole seconds for {@code Retry-After}.
 *
 * <p>Wait times are always rounded <em>up</em> (ceiling), never down: a
 * caller that waits the returned number of seconds is guaranteed to have
 * waited at least as long as the algorithm actually requires. Rounding down
 * could tell a client to retry before it is actually eligible.
 */
final class RetryAfter {

    private RetryAfter() {
    }

    static Duration ceilSeconds(long waitMillis) {
        long clampedMillis = Math.max(waitMillis, 0);
        long seconds = (clampedMillis + 999) / 1000;
        return Duration.ofSeconds(seconds);
    }
}
