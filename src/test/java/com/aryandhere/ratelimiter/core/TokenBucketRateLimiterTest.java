package com.aryandhere.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.time.ManualTimeSource;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void allowsRequestsBelowCapacity() {
        ManualTimeSource clock = new ManualTimeSource(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void allowsBurstUpToCapacity() {
        ManualTimeSource clock = new ManualTimeSource(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void rejectsRequestImmediatelyBeyondCapacity() {
        ManualTimeSource clock = new ManualTimeSource(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void partiallyRefillsTokensAfterElapsedTime() {
        ManualTimeSource clock = new ManualTimeSource(0);
        // capacity 2, refill 1 token/second
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        clock.advanceMillis(500); // half a token refilled, not enough for a full token
        assertFalse(limiter.tryAcquire("client-1"));

        clock.advanceMillis(500); // now a full second has passed since depletion started refilling
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void fullyRefillsButNeverExceedsCapacity() {
        ManualTimeSource clock = new ManualTimeSource(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));

        clock.advanceMillis(10_000); // far more than enough time to overfill

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void tracksEachClientIndependently() {
        ManualTimeSource clock = new ManualTimeSource(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, 1.0, new ManualTimeSource(0)));
    }

    @Test
    void rejectsNonPositiveRefillRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(1, 0.0, new ManualTimeSource(0)));
    }

    @Test
    void rejectsNullClientId() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(null));
    }

    @Test
    void rejectsBlankClientId() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, new ManualTimeSource(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(""));
    }

    @Test
    void concurrentRequestsNeverExceedCapacityForSingleClient() throws InterruptedException {
        int capacity = 20;
        int threadCount = 200;
        // Refill rate is negligible relative to the test duration, so capacity is the hard ceiling.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, 0.001, new ManualTimeSource(0));

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

        assertEquals(capacity, acceptedCount.get());
    }
}
