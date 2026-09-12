package com.aryandhere.ratelimiter.simulation;

import com.aryandhere.ratelimiter.stats.LatencyStats;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends concurrent {@code GET /api/resource} requests to a local Gatekeeper
 * server and measures the outcomes.
 *
 * <p>Work is executed on a fixed-size thread pool sized to {@code
 * concurrency}, in batches of at most {@code concurrency * 4} in-flight
 * tasks at a time (via repeated {@link ExecutorService#invokeAll}) so the
 * number of outstanding futures — and therefore memory use — stays bounded
 * regardless of the total request count. Response bodies are discarded;
 * only the status code and latency of each request are kept.
 */
public final class TrafficSimulator {

    private static final String RESOURCE_PATH = "/api/resource";
    private static final int BATCH_MULTIPLIER = 4;
    static final String WORKER_THREAD_NAME_PREFIX = "traffic-simulator-worker-";

    private final HttpClient httpClient;

    public TrafficSimulator(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SimulationSummary run(SimulatorConfig config) throws InterruptedException {
        URI target = config.baseUri().resolve(RESOURCE_PATH);
        Duration timeout = Duration.ofSeconds(config.requestTimeoutSeconds());
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency(), workerThreadFactory());
        try {
            long warmupCompleted = runWarmupPhase(config, target, timeout, executor);
            return runMeasuredPhase(config, target, timeout, executor, warmupCompleted);
        } finally {
            shutdown(executor);
        }
    }

    private long runWarmupPhase(SimulatorConfig config, URI target, Duration timeout, ExecutorService executor)
            throws InterruptedException {
        if (config.warmupRequests() == 0) {
            return 0;
        }
        List<Callable<RequestOutcomeOnly>> tasks = new ArrayList<>(config.warmupRequests());
        for (int i = 0; i < config.warmupRequests(); i++) {
            String clientId = config.clientIdPrefix() + "-" + ((i % config.clientCount()) + 1);
            tasks.add(() -> new RequestOutcomeOnly(sendOnce(target, clientId, timeout).outcome()));
        }
        // Results are intentionally discarded beyond their count: warmup exists to
        // establish connections and warm the JIT, not to be measured.
        List<RequestOutcomeOnly> results = executeBatched(tasks, executor, config.concurrency());
        return results.size();
    }

    private SimulationSummary runMeasuredPhase(
            SimulatorConfig config, URI target, Duration timeout, ExecutorService executor, long warmupCompleted)
            throws InterruptedException {
        List<Callable<RequestResult>> tasks = buildMeasuredTasks(config, target, timeout);

        long startNanos = System.nanoTime();
        List<RequestResult> results = executeBatched(tasks, executor, config.concurrency());
        long elapsedNanos = System.nanoTime() - startNanos;

        return summarize(config, warmupCompleted, results, elapsedNanos);
    }

    private List<Callable<RequestResult>> buildMeasuredTasks(SimulatorConfig config, URI target, Duration timeout) {
        List<Callable<RequestResult>> tasks =
                new ArrayList<>(config.clientCount() * config.requestsPerClient());
        // Round-robin across clients so the request stream interleaves clients
        // instead of running one client fully before the next.
        for (int round = 0; round < config.requestsPerClient(); round++) {
            for (int clientIndex = 0; clientIndex < config.clientCount(); clientIndex++) {
                String clientId = config.clientIdPrefix() + "-" + (clientIndex + 1);
                int capturedClientIndex = clientIndex;
                tasks.add(() -> {
                    Attempt attempt = sendOnce(target, clientId, timeout);
                    return new RequestResult(capturedClientIndex, attempt.outcome(), attempt.latencyNanos());
                });
            }
        }
        return tasks;
    }

    private Attempt sendOnce(URI target, String clientId, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(target)
                .timeout(timeout)
                .header("X-Client-Id", clientId)
                .GET()
                .build();
        long startNanos = System.nanoTime();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyNanos = System.nanoTime() - startNanos;
            return new Attempt(categorize(response.statusCode()), latencyNanos);
        } catch (IOException e) {
            long latencyNanos = System.nanoTime() - startNanos;
            return new Attempt(RequestOutcome.TRANSPORT_ERROR, latencyNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latencyNanos = System.nanoTime() - startNanos;
            return new Attempt(RequestOutcome.TRANSPORT_ERROR, latencyNanos);
        }
    }

    private static RequestOutcome categorize(int statusCode) {
        if (statusCode == 200) {
            return RequestOutcome.ALLOWED;
        }
        if (statusCode == 429) {
            return RequestOutcome.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return RequestOutcome.SERVER_ERROR;
        }
        // Includes ordinary 4xx and any other status Gatekeeper is not expected to return.
        return RequestOutcome.CLIENT_ERROR;
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
                    throw new IllegalStateException("Simulated request task failed unexpectedly", e.getCause());
                }
            }
            index = end;
        }
        return results;
    }

    private SimulationSummary summarize(
            SimulatorConfig config, long warmupCompleted, List<RequestResult> results, long elapsedNanos) {
        long allowed = 0;
        long rateLimited = 0;
        long clientErrors = 0;
        long serverErrors = 0;
        long transportErrors = 0;
        long[] latencies = new long[results.size()];
        long[] acceptedPerClient = new long[config.clientCount()];
        long[] rejectedPerClient = new long[config.clientCount()];
        long[] otherPerClient = new long[config.clientCount()];

        for (int i = 0; i < results.size(); i++) {
            RequestResult result = results.get(i);
            latencies[i] = result.latencyNanos();
            switch (result.outcome()) {
                case ALLOWED -> {
                    allowed++;
                    acceptedPerClient[result.clientIndex()]++;
                }
                case RATE_LIMITED -> {
                    rateLimited++;
                    rejectedPerClient[result.clientIndex()]++;
                }
                case CLIENT_ERROR -> {
                    clientErrors++;
                    otherPerClient[result.clientIndex()]++;
                }
                case SERVER_ERROR -> {
                    serverErrors++;
                    otherPerClient[result.clientIndex()]++;
                }
                case TRANSPORT_ERROR -> {
                    transportErrors++;
                    otherPerClient[result.clientIndex()]++;
                }
            }
        }

        List<ClientOutcomeSummary> perClient = new ArrayList<>(config.clientCount());
        long minAccepted = Long.MAX_VALUE;
        long maxAccepted = Long.MIN_VALUE;
        long totalAccepted = 0;
        for (int clientIndex = 0; clientIndex < config.clientCount(); clientIndex++) {
            String clientId = config.clientIdPrefix() + "-" + (clientIndex + 1);
            long accepted = acceptedPerClient[clientIndex];
            perClient.add(new ClientOutcomeSummary(clientId, accepted, rejectedPerClient[clientIndex],
                    otherPerClient[clientIndex]));
            minAccepted = Math.min(minAccepted, accepted);
            maxAccepted = Math.max(maxAccepted, accepted);
            totalAccepted += accepted;
        }
        double meanAccepted = totalAccepted / (double) config.clientCount();
        boolean consistent = (maxAccepted - minAccepted) <= Math.max(1, (long) Math.ceil(0.1 * meanAccepted));

        return new SimulationSummary(
                warmupCompleted,
                allowed,
                rateLimited,
                clientErrors,
                serverErrors,
                transportErrors,
                elapsedNanos,
                LatencyStats.of(latencies),
                perClient,
                minAccepted,
                maxAccepted,
                consistent);
    }

    private static ThreadFactory workerThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> new Thread(runnable, WORKER_THREAD_NAME_PREFIX + counter.getAndIncrement());
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private record RequestOutcomeOnly(RequestOutcome outcome) {
    }

    private record Attempt(RequestOutcome outcome, long latencyNanos) {
    }
}
