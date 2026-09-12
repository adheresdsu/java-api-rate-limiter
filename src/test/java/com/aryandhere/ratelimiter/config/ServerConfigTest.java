package com.aryandhere.ratelimiter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServerConfigTest {

    @Test
    void rejectsPortOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(0, AlgorithmType.TOKEN_BUCKET, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(65536, AlgorithmType.TOKEN_BUCKET, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 0, 1, 1, 1, 1, 1));
    }

    @Test
    void rejectsNonPositiveWindowSeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 1, 0, 1, 1, 1, 1));
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 1, 1, 0, 1, 1, 1));
    }

    @Test
    void rejectsNonPositiveRefillTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 1, 1, 1, 0, 1, 1));
    }

    @Test
    void rejectsNonPositiveRefillPeriodSeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 1, 1, 1, 1, 0, 1));
    }

    @Test
    void rejectsNonPositiveWorkers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(8080, AlgorithmType.TOKEN_BUCKET, 1, 1, 1, 1, 1, 0));
    }
}
