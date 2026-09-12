package com.aryandhere.ratelimiter.time;

/**
 * Supplies the current time to rate limiting algorithms.
 *
 * <p>Algorithms depend on this abstraction instead of calling
 * {@link System#currentTimeMillis()} directly, so tests can advance time
 * deterministically without {@code Thread.sleep}.
 */
public interface TimeSource {

    /**
     * Returns the current time in milliseconds since the epoch.
     *
     * @return the current time, as would be returned by
     *     {@link System#currentTimeMillis()}
     */
    long currentTimeMillis();
}
