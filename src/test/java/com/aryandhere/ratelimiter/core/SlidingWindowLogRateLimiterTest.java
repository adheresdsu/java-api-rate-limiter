package com.aryandhere.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class SlidingWindowLogRateLimiterTest {

    @Test
    void allowsRequestsBelowLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(3, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void allowsExactlyTheLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(3, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void rejectsRequestImmediatelyBeyondLimit() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(2, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void allowsRequestOnceOldestTimestampSlidesOutOfWindow() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(2, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1")); // t=0
        clock.advanceMillis(500);
        assertTrue(limiter.tryAcquire("client-1")); // t=500
        assertFalse(limiter.tryAcquire("client-1")); // still t=500, both requests within last 1000ms

        clock.advanceMillis(501); // t=1001, the t=0 request has slid out of the window
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void tracksEachClientIndependently() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(1, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));
    }

    @Test
    void decisionReportsRemainingAndNoRetryAfterWhenAllowed() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(3, Duration.ofSeconds(1), clock);

        RateLimitDecision decision = limiter.decide("client-1");

        assertTrue(decision.allowed());
        assertEquals(3, decision.limit());
        assertEquals(2, decision.remaining());
        assertNull(decision.retryAfter());
    }

    @Test
    void decisionReportsZeroRemainingAndRetryAfterWhenRejected() {
        ManualTimeSource clock = new ManualTimeSource(0);
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(1, Duration.ofSeconds(4), clock);

        limiter.decide("client-1");
        RateLimitDecision decision = limiter.decide("client-1");

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
        assertNotNull(decision.retryAfter());
        assertTrue(decision.retryAfter().toMillis() > 0);
        assertTrue(decision.retryAfter().toSeconds() <= 4);
    }

    @Test
    void rejectsNonPositiveMaxRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogRateLimiter(0, Duration.ofSeconds(1), new ManualTimeSource(0)));
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogRateLimiter(1, Duration.ZERO, new ManualTimeSource(0)));
    }

    @Test
    void rejectsNullClientId() {
        SlidingWindowLogRateLimiter limiter =
                new SlidingWindowLogRateLimiter(1, Duration.ofSeconds(1), new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(null));
    }

    @Test
    void rejectsBlankClientId() {
        SlidingWindowLogRateLimiter limiter =
                new SlidingWindowLogRateLimiter(1, Duration.ofSeconds(1), new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(" "));
    }

    @Test
    void concurrentRequestsNeverExceedLimitForSingleClient() throws InterruptedException {
        int limit = 20;
        int threadCount = 200;
        SlidingWindowLogRateLimiter limiter =
                new SlidingWindowLogRateLimiter(limit, Duration.ofSeconds(30), new ManualTimeSource(0));

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
