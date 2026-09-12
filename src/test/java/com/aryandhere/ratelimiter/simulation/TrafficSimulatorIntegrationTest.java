package com.aryandhere.ratelimiter.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.core.FixedWindowRateLimiter;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.http.GatekeeperHttpServer;
import com.aryandhere.ratelimiter.time.SystemTimeSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that run the simulator against a real {@link
 * GatekeeperHttpServer} bound to an ephemeral local port. Every run uses
 * small, bounded workloads so tests finish quickly and cannot hang.
 */
class TrafficSimulatorIntegrationTest {

    private GatekeeperHttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private URI startServer(RateLimiter rateLimiter) throws Exception {
        server = GatekeeperHttpServer.start(0, 4, rateLimiter);
        return new URI("http://localhost:" + server.port());
    }

    private SimulatorConfig configFor(URI baseUri, int clientCount, int requestsPerClient, int concurrency,
            int warmupRequests) {
        return new SimulatorConfig(baseUri, clientCount, requestsPerClient, concurrency, 5, "sim-client",
                warmupRequests);
    }

    @Test
    void shortSuccessfulSimulationAllowsEveryRequestUnderAGenerousLimit() throws Exception {
        URI baseUri = startServer(new FixedWindowRateLimiter(1000, Duration.ofSeconds(60), new SystemTimeSource()));
        TrafficSimulator simulator = new TrafficSimulator(HttpClient.newHttpClient());

        SimulationSummary summary = simulator.run(configFor(baseUri, 5, 4, 2, 4));

        assertEquals(4, summary.warmupRequestsCompleted());
        assertEquals(20, summary.totalMeasuredRequests());
        assertEquals(20, summary.allowed());
        assertEquals(0, summary.rateLimited());
        assertEquals(0, summary.clientErrors());
        assertEquals(0, summary.serverErrors());
        assertEquals(0, summary.transportErrors());
        for (ClientOutcomeSummary client : summary.perClient()) {
            assertEquals(4, client.accepted());
        }
    }

    @Test
    void mixedAllowedAndRateLimitedResponsesAreAccountedExactly() throws Exception {
        URI baseUri = startServer(new FixedWindowRateLimiter(2, Duration.ofSeconds(60), new SystemTimeSource()));
        TrafficSimulator simulator = new TrafficSimulator(HttpClient.newHttpClient());

        SimulationSummary summary = simulator.run(configFor(baseUri, 4, 5, 3, 0));

        assertEquals(20, summary.totalMeasuredRequests());
        assertEquals(8, summary.allowed()); // 4 clients x 2 allowed each
        assertEquals(12, summary.rateLimited()); // 4 clients x 3 rejected each
        assertEquals(summary.totalMeasuredRequests(),
                summary.allowed() + summary.rateLimited() + summary.clientErrors()
                        + summary.serverErrors() + summary.transportErrors());
        for (ClientOutcomeSummary client : summary.perClient()) {
            assertEquals(2, client.accepted());
            assertEquals(3, client.rejected());
        }
        assertTrue(summary.consistentClientDistribution());
    }

    @Test
    void categoryTotalsAlwaysEqualAttemptedRequests() throws Exception {
        URI baseUri = startServer(new FixedWindowRateLimiter(3, Duration.ofSeconds(60), new SystemTimeSource()));
        TrafficSimulator simulator = new TrafficSimulator(HttpClient.newHttpClient());

        SimulationSummary summary = simulator.run(configFor(baseUri, 3, 7, 2, 5));

        long attempted = 3L * 7L;
        long categorized = summary.allowed() + summary.rateLimited() + summary.clientErrors()
                + summary.serverErrors() + summary.transportErrors();
        assertEquals(attempted, categorized);
        assertEquals(attempted, summary.latency().count());
    }

    @Test
    void latencyPercentilesAreOrderedCorrectly() throws Exception {
        URI baseUri = startServer(new FixedWindowRateLimiter(100, Duration.ofSeconds(60), new SystemTimeSource()));
        TrafficSimulator simulator = new TrafficSimulator(HttpClient.newHttpClient());

        SimulationSummary summary = simulator.run(configFor(baseUri, 4, 10, 4, 0));

        assertTrue(summary.latency().minNanos() <= summary.latency().p50Nanos());
        assertTrue(summary.latency().p50Nanos() <= summary.latency().p95Nanos());
        assertTrue(summary.latency().p95Nanos() <= summary.latency().p99Nanos());
        assertTrue(summary.latency().p99Nanos() <= summary.latency().maxNanos());
    }

    @Test
    void noWorkerThreadsRemainAliveAfterSimulationCompletes() throws Exception {
        URI baseUri = startServer(new FixedWindowRateLimiter(100, Duration.ofSeconds(60), new SystemTimeSource()));
        TrafficSimulator simulator = new TrafficSimulator(HttpClient.newHttpClient());

        simulator.run(configFor(baseUri, 3, 3, 2, 0));

        long aliveWorkerThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(TrafficSimulator.WORKER_THREAD_NAME_PREFIX))
                .filter(Thread::isAlive)
                .count();
        assertEquals(0, aliveWorkerThreads);
    }
}
