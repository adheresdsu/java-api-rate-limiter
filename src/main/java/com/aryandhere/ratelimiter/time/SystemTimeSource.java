package com.aryandhere.ratelimiter.time;

/** {@link TimeSource} backed by the JVM's wall clock. */
public final class SystemTimeSource implements TimeSource {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
