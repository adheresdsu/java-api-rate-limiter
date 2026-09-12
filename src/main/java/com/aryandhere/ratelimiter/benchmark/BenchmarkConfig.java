package com.aryandhere.ratelimiter.benchmark;

/**
 * Validated configuration for the direct algorithm benchmark.
 *
 * <p>The safe maximums exist so a mistyped argument cannot allocate
 * unbounded memory for latency samples or spin up an unbounded number of
 * tasks.
 *
 * @param clients number of deterministic client identifiers used per trial
 * @param operationsPerClient measured {@code decide()} calls per client, per trial
 * @param concurrency number of worker threads
 * @param warmupOperations unmeasured operations run (with a throwaway limiter) before each trial
 * @param trials number of independent measured trials per algorithm
 */
public record BenchmarkConfig(int clients, int operationsPerClient, int concurrency, int warmupOperations, int trials) {

    public static final int DEFAULT_CLIENTS = 10;
    public static final int DEFAULT_OPERATIONS_PER_CLIENT = 100;
    public static final int DEFAULT_CONCURRENCY = 4;
    public static final int DEFAULT_WARMUP_OPERATIONS = 1_000;
    public static final int DEFAULT_TRIALS = 3;

    public static final int MAX_CLIENTS = 100_000;
    public static final int MAX_OPERATIONS_PER_CLIENT = 1_000_000;
    public static final int MAX_CONCURRENCY = 256;
    public static final int MAX_WARMUP_OPERATIONS = 10_000_000;
    public static final int MAX_TRIALS = 50;
    public static final long MAX_TOTAL_MEASURED_OPERATIONS = 20_000_000L;
    public static final int MINIMUM_TRIALS_FOR_MEDIAN = 3;

    public BenchmarkConfig {
        if (clients <= 0) {
            throw new IllegalArgumentException("clients must be positive, got " + clients);
        }
        if (clients > MAX_CLIENTS) {
            throw new IllegalArgumentException("clients must not exceed " + MAX_CLIENTS);
        }
        if (operationsPerClient <= 0) {
            throw new IllegalArgumentException("operationsPerClient must be positive, got " + operationsPerClient);
        }
        if (operationsPerClient > MAX_OPERATIONS_PER_CLIENT) {
            throw new IllegalArgumentException("operationsPerClient must not exceed " + MAX_OPERATIONS_PER_CLIENT);
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive, got " + concurrency);
        }
        if (concurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException("concurrency must not exceed " + MAX_CONCURRENCY);
        }
        if (warmupOperations < 0) {
            throw new IllegalArgumentException("warmupOperations must not be negative, got " + warmupOperations);
        }
        if (warmupOperations > MAX_WARMUP_OPERATIONS) {
            throw new IllegalArgumentException("warmupOperations must not exceed " + MAX_WARMUP_OPERATIONS);
        }
        if (trials < MINIMUM_TRIALS_FOR_MEDIAN) {
            throw new IllegalArgumentException("trials must be at least " + MINIMUM_TRIALS_FOR_MEDIAN
                    + " so a median is meaningful, got " + trials);
        }
        if (trials > MAX_TRIALS) {
            throw new IllegalArgumentException("trials must not exceed " + MAX_TRIALS);
        }
        long totalMeasuredOperations = (long) clients * (long) operationsPerClient;
        if (totalMeasuredOperations > MAX_TOTAL_MEASURED_OPERATIONS) {
            throw new IllegalArgumentException("clients * operationsPerClient must not exceed "
                    + MAX_TOTAL_MEASURED_OPERATIONS + ", got " + totalMeasuredOperations);
        }
    }

    public static BenchmarkConfig defaults() {
        return new BenchmarkConfig(DEFAULT_CLIENTS, DEFAULT_OPERATIONS_PER_CLIENT, DEFAULT_CONCURRENCY,
                DEFAULT_WARMUP_OPERATIONS, DEFAULT_TRIALS);
    }

    public long totalMeasuredOperations() {
        return (long) clients * (long) operationsPerClient;
    }
}
