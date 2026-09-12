package com.aryandhere.ratelimiter.benchmark;

import com.aryandhere.ratelimiter.config.AlgorithmType;
import com.aryandhere.ratelimiter.core.FixedWindowRateLimiter;
import com.aryandhere.ratelimiter.core.RateLimitDecision;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.core.SlidingWindowLogRateLimiter;
import com.aryandhere.ratelimiter.core.TokenBucketRateLimiter;
import com.aryandhere.ratelimiter.stats.LatencyStats;
import com.aryandhere.ratelimiter.time.SystemTimeSource;
import com.aryandhere.ratelimiter.time.TimeSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compares {@link FixedWindowRateLimiter}, {@link SlidingWindowLogRateLimiter},
 * and {@link TokenBucketRateLimiter} by calling them directly (no HTTP), so
 * HTTP overhead is never mixed into the measured cost of the algorithms
 * themselves.
 *
 * <p><strong>Synthetic limiter configuration:</strong> for every algorithm
 * and trial, the per-client limit is fixed at {@code max(1,
 * operationsPerClient / 2)}, with a window (or bucket capacity) large enough
 * that it will not reset or meaningfully refill during a single trial. This
 * makes every trial deterministically produce roughly half accepted and
 * half rejected decisions per client, so every algorithm's rejection path is
 * exercised identically — it is a fixed benchmark harness parameter, not a
 * general-purpose recommendation.
 *
 * <p>Each (trial, algorithm) pair gets its own fresh warmup limiter (results
 * discarded) and its own fresh measured limiter, so no state leaks between
 * trials. Algorithm order is rotated by one position each trial to avoid any
 * one algorithm consistently running first or last.
 */
public final class BenchmarkRunner {

    private static final List<AlgorithmType> ALGORITHMS =
            List.of(AlgorithmType.FIXED_WINDOW, AlgorithmType.SLIDING_WINDOW, AlgorithmType.TOKEN_BUCKET);
    private static final Duration BENCHMARK_WINDOW = Duration.ofHours(1);
    private static final double NEGLIGIBLE_REFILL_RATE_PER_SECOND = 1.0 / 3600.0;
    private static final int BATCH_MULTIPLIER = 4;
    static final String WORKER_THREAD_NAME_PREFIX = "algorithm-benchmark-worker-";

    public Map<AlgorithmType, List<TrialResult>> run(BenchmarkConfig config) throws InterruptedException {
        Map<AlgorithmType, List<TrialResult>> resultsByAlgorithm = new EnumMap<>(AlgorithmType.class);
        for (AlgorithmType algorithm : ALGORITHMS) {
            resultsByAlgorithm.put(algorithm, new ArrayList<>(config.trials()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency(), workerThreadFactory());
        try {
            for (int trialIndex = 0; trialIndex < config.trials(); trialIndex++) {
                for (AlgorithmType algorithm : rotatedOrder(trialIndex)) {
                    runWarmup(config, algorithm, executor);
                    TrialResult result = runMeasuredTrial(config, algorithm, trialIndex, executor);
                    resultsByAlgorithm.get(algorithm).add(result);
                }
            }
        } finally {
            shutdown(executor);
        }
        return resultsByAlgorithm;
    }

    private List<AlgorithmType> rotatedOrder(int trialIndex) {
        int n = ALGORITHMS.size();
        List<AlgorithmType> rotated = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            rotated.add(ALGORITHMS.get((trialIndex + k) % n));
        }
        return rotated;
    }

    private void runWarmup(BenchmarkConfig config, AlgorithmType algorithm, ExecutorService executor)
            throws InterruptedException {
        if (config.warmupOperations() == 0) {
            return;
        }
        RateLimiter warmupLimiter = newLimiter(algorithm, config, new SystemTimeSource());
        List<Callable<Boolean>> tasks = new ArrayList<>(config.warmupOperations());
        for (int i = 0; i < config.warmupOperations(); i++) {
            String clientId = "bench-client-" + ((i % config.clients()) + 1);
            tasks.add(() -> warmupLimiter.tryAcquire(clientId));
        }
        executeBatched(tasks, executor, config.concurrency());
    }

    private TrialResult runMeasuredTrial(
            BenchmarkConfig config, AlgorithmType algorithm, int trialIndex, ExecutorService executor)
            throws InterruptedException {
        RateLimiter limiter = newLimiter(algorithm, config, new SystemTimeSource());
        List<Callable<Operation>> tasks = buildOperations(config, limiter);

        long startNanos = System.nanoTime();
        List<Operation> operations = executeBatched(tasks, executor, config.concurrency());
        long elapsedNanos = System.nanoTime() - startNanos;

        long accepted = 0;
        long rejected = 0;
        long[] latencies = new long[operations.size()];
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            latencies[i] = op.latencyNanos();
            if (op.allowed()) {
                accepted++;
            } else {
                rejected++;
            }
        }

        LatencyStats latencyStats = LatencyStats.of(latencies);
        return new TrialResult(algorithm.cliName(), trialIndex, accepted + rejected, accepted, rejected,
                elapsedNanos, latencyStats);
    }

    private List<Callable<Operation>> buildOperations(BenchmarkConfig config, RateLimiter limiter) {
        List<Callable<Operation>> tasks = new ArrayList<>(config.clients() * config.operationsPerClient());
        for (int round = 0; round < config.operationsPerClient(); round++) {
            for (int clientIndex = 0; clientIndex < config.clients(); clientIndex++) {
                String clientId = "bench-client-" + (clientIndex + 1);
                tasks.add(() -> {
                    long startNanos = System.nanoTime();
                    RateLimitDecision decision = limiter.decide(clientId);
                    long latencyNanos = System.nanoTime() - startNanos;
                    return new Operation(decision.allowed(), latencyNanos);
                });
            }
        }
        return tasks;
    }

    private RateLimiter newLimiter(AlgorithmType algorithm, BenchmarkConfig config, TimeSource timeSource) {
        long limit = Math.max(1, config.operationsPerClient() / 2);
        return switch (algorithm) {
            case FIXED_WINDOW -> new FixedWindowRateLimiter((int) limit, BENCHMARK_WINDOW, timeSource);
            case SLIDING_WINDOW -> new SlidingWindowLogRateLimiter((int) limit, BENCHMARK_WINDOW, timeSource);
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(limit, NEGLIGIBLE_REFILL_RATE_PER_SECOND, timeSource);
        };
    }

    private <T> List<T> executeBatched(List<Callable<T>> tasks, ExecutorService executor, int concurrency)
            throws InterruptedException {
        int batchSize = concurrency * BATCH_MULTIPLIER;
        List<T> results = new ArrayList<>(tasks.size());
        int index = 0;
        while (index < tasks.size()) {
            int end = Math.min(index + batchSize, tasks.size());
            List<Future<T>> futures = executor.invokeAll(tasks.subList(index, end));
            for (Future<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Benchmark operation failed unexpectedly", e.getCause());
                }
            }
            index = end;
        }
        return results;
    }

    private static ThreadFactory workerThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> new Thread(runnable, WORKER_THREAD_NAME_PREFIX + counter.getAndIncrement());
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private record Operation(boolean allowed, long latencyNanos) {
    }
}
