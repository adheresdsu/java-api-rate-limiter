package com.aryandhere.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.time.ManualTimeSource;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void allowsRequestsBelowLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void allowsExactlyTheLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void rejectsRequestImmediatelyBeyondLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void resetsCountAfterWindowExpires() {
        ManualTimeSource clock = new ManualTimeSource(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        clock.advanceMillis(1000);

        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void tracksEachClientIndependently() {
        ManualTimeSource clock = new ManualTimeSource(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));
    }

    @Test
    void rejectsNonPositiveMaxRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(0, Duration.ofSeconds(1), new ManualTimeSource(0)));
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(1, Duration.ZERO, new ManualTimeSource(0)));
    }

    @Test
    void rejectsNullClientId() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(1), new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(null));
    }

    @Test
    void rejectsBlankClientId() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(1), new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("   "));
    }

    @Test
    void concurrentRequestsNeverExceedLimitForSingleClient() throws InterruptedException {
        int limit = 20;
        int threadCount = 200;
        FixedWindowRateLimiter limiter =
                new FixedWindowRateLimiter(limit, Duration.ofSeconds(30), new ManualTimeSource(0));

        AtomicInteger acceptedCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        if (limiter.tryAcquire("shared-client")) {
                            acceptedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(limit, acceptedCount.get());
    }
}
