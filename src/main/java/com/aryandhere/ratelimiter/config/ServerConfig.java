package com.aryandhere.ratelimiter.config;

/**
 * Validated server configuration.
 *
 * <p>Only the fields relevant to the selected {@link #algorithm()} are used
 * by {@link RateLimiterFactory}: {@code limit}/{@code windowSeconds} for the
 * window-based algorithms, {@code capacity}/{@code refillTokens}/{@code
 * refillPeriodSeconds} for the token bucket.
 *
 * @param port TCP port to listen on
 * @param algorithm which rate limiting algorithm to serve requests with
 * @param limit requests allowed per window (fixed-window, sliding-window)
 * @param windowSeconds window length in seconds (fixed-window, sliding-window)
 * @param capacity bucket capacity / max burst size (token-bucket)
 * @param refillTokens tokens added per refill period (token-bucket)
 * @param refillPeriodSeconds refill period in seconds (token-bucket)
 * @param workers number of worker threads in the HTTP server's thread pool
 */
public record ServerConfig(
        int port,
        AlgorithmType algorithm,
        int limit,
        long windowSeconds,
        long capacity,
        double refillTokens,
        double refillPeriodSeconds,
        int workers) {

    public static final int DEFAULT_PORT = 8080;
    public static final AlgorithmType DEFAULT_ALGORITHM = AlgorithmType.TOKEN_BUCKET;
    public static final int DEFAULT_LIMIT = 100;
    public static final long DEFAULT_WINDOW_SECONDS = 60;
    public static final long DEFAULT_CAPACITY = 20;
    public static final double DEFAULT_REFILL_TOKENS = 10;
    public static final double DEFAULT_REFILL_PERIOD_SECONDS = 1;
    public static final int DEFAULT_WORKERS = 16;

    public ServerConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + limit);
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive, got " + windowSeconds);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (!(refillTokens > 0)) {
            throw new IllegalArgumentException("refillTokens must be positive, got " + refillTokens);
        }
        if (!(refillPeriodSeconds > 0)) {
            throw new IllegalArgumentException("refillPeriodSeconds must be positive, got " + refillPeriodSeconds);
        }
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive, got " + workers);
        }
    }

    /** Returns the defaults with every field set to its documented default value. */
    public static ServerConfig defaults() {
        return new ServerConfig(
                DEFAULT_PORT,
                DEFAULT_ALGORITHM,
                DEFAULT_LIMIT,
                DEFAULT_WINDOW_SECONDS,
                DEFAULT_CAPACITY,
                DEFAULT_REFILL_TOKENS,
                DEFAULT_REFILL_PERIOD_SECONDS,
                DEFAULT_WORKERS);
    }
}
