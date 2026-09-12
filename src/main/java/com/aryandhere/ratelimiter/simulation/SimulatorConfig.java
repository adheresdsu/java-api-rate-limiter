package com.aryandhere.ratelimiter.simulation;

import java.net.URI;

/**
 * Validated configuration for the traffic simulator.
 *
 * <p>{@code baseUri} is guaranteed by {@link LocalTargetValidator} to be a
 * plain {@code http} URL pointing at {@code localhost}, {@code 127.0.0.1},
 * or {@code ::1}. The safe maximums below exist so a mistyped argument
 * cannot spin up an unbounded number of tasks or allocate unbounded memory
 * for latency samples.
 *
 * @param baseUri the local Gatekeeper server to target
 * @param clientCount number of simulated clients, each with its own {@code X-Client-Id}
 * @param requestsPerClient measured requests sent by each client
 * @param concurrency number of worker threads (and the HTTP client's executor size)
 * @param requestTimeoutSeconds per-request timeout
 * @param clientIdPrefix prefix used to build each client's deterministic {@code X-Client-Id}
 * @param warmupRequests requests sent before measurement begins, excluded from all reported statistics
 */
public record SimulatorConfig(
        URI baseUri,
        int clientCount,
        int requestsPerClient,
        int concurrency,
        int requestTimeoutSeconds,
        String clientIdPrefix,
        int warmupRequests) {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";
    public static final int DEFAULT_CLIENT_COUNT = 10;
    public static final int DEFAULT_REQUESTS_PER_CLIENT = 20;
    public static final int DEFAULT_CONCURRENCY = 8;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;
    public static final String DEFAULT_CLIENT_ID_PREFIX = "sim-client";
    public static final int DEFAULT_WARMUP_REQUESTS = 0;

    /** Documented safe upper bounds, chosen so a typo cannot create unbounded work. */
    public static final int MAX_CLIENT_COUNT = 10_000;
    public static final int MAX_REQUESTS_PER_CLIENT = 100_000;
    public static final int MAX_CONCURRENCY = 256;
    public static final int MAX_WARMUP_REQUESTS = 100_000;
    public static final long MAX_TOTAL_MEASURED_REQUESTS = 2_000_000L;

    public SimulatorConfig {
        if (clientCount <= 0) {
            throw new IllegalArgumentException("clientCount must be positive, got " + clientCount);
        }
        if (clientCount > MAX_CLIENT_COUNT) {
            throw new IllegalArgumentException("clientCount must not exceed " + MAX_CLIENT_COUNT);
        }
        if (requestsPerClient <= 0) {
            throw new IllegalArgumentException("requestsPerClient must be positive, got " + requestsPerClient);
        }
        if (requestsPerClient > MAX_REQUESTS_PER_CLIENT) {
            throw new IllegalArgumentException("requestsPerClient must not exceed " + MAX_REQUESTS_PER_CLIENT);
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive, got " + concurrency);
        }
        if (concurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException("concurrency must not exceed " + MAX_CONCURRENCY);
        }
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "requestTimeoutSeconds must be positive, got " + requestTimeoutSeconds);
        }
        if (clientIdPrefix == null || clientIdPrefix.isBlank()) {
            throw new IllegalArgumentException("clientIdPrefix must not be null or blank");
        }
        if (warmupRequests < 0) {
            throw new IllegalArgumentException("warmupRequests must not be negative, got " + warmupRequests);
        }
        if (warmupRequests > MAX_WARMUP_REQUESTS) {
            throw new IllegalArgumentException("warmupRequests must not exceed " + MAX_WARMUP_REQUESTS);
        }
        long totalMeasuredRequests = (long) clientCount * (long) requestsPerClient;
        if (totalMeasuredRequests > MAX_TOTAL_MEASURED_REQUESTS) {
            throw new IllegalArgumentException("clientCount * requestsPerClient must not exceed "
                    + MAX_TOTAL_MEASURED_REQUESTS + ", got " + totalMeasuredRequests);
        }
    }

    public long totalMeasuredRequests() {
        return (long) clientCount * (long) requestsPerClient;
    }
}
