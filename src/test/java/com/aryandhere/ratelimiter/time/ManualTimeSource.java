package com.aryandhere.ratelimiter.time;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test double that lets tests control the current time explicitly instead of
 * relying on {@code Thread.sleep} and the wall clock.
 */
public final class ManualTimeSource implements TimeSource {

    private final AtomicLong currentMillis;

    public ManualTimeSource(long initialMillis) {
        this.currentMillis = new AtomicLong(initialMillis);
    }

    @Override
    public long currentTimeMillis() {
        return currentMillis.get();
    }

    public void advanceMillis(long millis) {
        currentMillis.addAndGet(millis);
    }

    public void set(long millis) {
        currentMillis.set(millis);
    }
}
