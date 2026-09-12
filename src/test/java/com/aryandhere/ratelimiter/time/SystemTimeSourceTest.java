package com.aryandhere.ratelimiter.time;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemTimeSourceTest {

    @Test
    void reportsTimeCloseToTheWallClock() {
        SystemTimeSource timeSource = new SystemTimeSource();

        long before = System.currentTimeMillis();
        long reported = timeSource.currentTimeMillis();
        long after = System.currentTimeMillis();

        assertTrue(reported >= before && reported <= after);
    }
}
