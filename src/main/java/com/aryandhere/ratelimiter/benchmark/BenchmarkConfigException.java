package com.aryandhere.ratelimiter.benchmark;

/** Thrown when algorithm benchmark command-line configuration is missing, malformed, or unsafe. */
public final class BenchmarkConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    public BenchmarkConfigException(String message) {
        super(message);
    }
}
