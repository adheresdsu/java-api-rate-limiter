package com.aryandhere.ratelimiter.core;

import java.time.Duration;

/** Shared argument validation for rate limiter constructors and methods. */
final class Validation {

    private Validation() {
    }

    static String requireClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        return clientId;
    }

    static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    static double requirePositive(double value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be a positive duration, got " + value);
        }
        return value;
    }
}
