package com.aryandhere.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

    @Test
    void allowCarriesNoRetryAfter() {
        RateLimitDecision decision = RateLimitDecision.allow(10, 9);

        assertTrue(decision.allowed());
        assertEquals(10, decision.limit());
        assertEquals(9, decision.remaining());
        assertNull(decision.retryAfter());
    }

    @Test
    void rejectAlwaysReportsZeroRemaining() {
        RateLimitDecision decision = RateLimitDecision.reject(10, Duration.ofSeconds(2));

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
        assertEquals(Duration.ofSeconds(2), decision.retryAfter());
    }

    @Test
    void rejectsNegativeLimit() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitDecision(true, -1, 0, null));
    }

    @Test
    void rejectsNegativeRemaining() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitDecision(true, 1, -1, null));
    }

    @Test
    void rejectsNegativeRetryAfter() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitDecision(false, 1, 0, Duration.ofSeconds(-1)));
    }
}
