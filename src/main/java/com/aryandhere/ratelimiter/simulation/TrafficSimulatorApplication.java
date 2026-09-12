package com.aryandhere.ratelimiter.simulation;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

/** Command-line entry point for the concurrent HTTP traffic simulator. */
public final class TrafficSimulatorApplication {

    private TrafficSimulatorApplication() {
    }

    public static void main(String[] args) {
        SimulatorConfig config = parseConfigOrExit(args);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                .build();
        TrafficSimulator simulator = new TrafficSimulator(httpClient);

        System.out.println("Target: " + config.baseUri());
        System.out.println("Clients: " + config.clientCount() + " x " + config.requestsPerClient()
                + " requests, concurrency=" + config.concurrency());
        if (config.warmupRequests() > 0) {
            System.out.println("Warmup requests: " + config.warmupRequests() + " (excluded from results below)");
        }

        SimulationSummary summary;
        try {
            summary = simulator.run(config);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error: simulation was interrupted");
            System.exit(1);
            return;
        }

        printSummary(summary);
    }

    private static SimulatorConfig parseConfigOrExit(String[] args) {
        try {
            return SimulatorArgsParser.parse(args);
        } catch (SimulatorConfigException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.print(SimulatorArgsParser.usage());
            System.exit(1);
            throw new AssertionError("unreachable");
        }
    }

    private static void printSummary(SimulationSummary summary) {
        System.out.println();
        System.out.println("=== Results (measured phase only) ===");
        System.out.println("Total requests:   " + summary.totalMeasuredRequests());
        System.out.println("Allowed (200):    " + summary.allowed());
        System.out.println("Rate limited(429):" + summary.rateLimited());
        System.out.println("Client errors:    " + summary.clientErrors());
        System.out.println("Server errors:    " + summary.serverErrors());
        System.out.println("Transport errors: " + summary.transportErrors());
        System.out.printf(Locale.ROOT, "Elapsed:          %.3f s%n", summary.elapsedNanos() / 1_000_000_000.0);
        System.out.printf(Locale.ROOT, "Throughput:       %.1f req/s%n", summary.throughputRequestsPerSecond());
        System.out.println();
        System.out.println("=== Latency (ms) ===");
        System.out.printf(Locale.ROOT, "min=%.3f mean=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f%n",
                summary.latency().minMillis(), summary.latency().meanMillis(), summary.latency().p50Millis(),
                summary.latency().p95Millis(), summary.latency().p99Millis(), summary.latency().maxMillis());
        System.out.println();
        System.out.println("=== Client-distribution consistency ===");
        System.out.println("Min accepted per client: " + summary.minAcceptedAcrossClients());
        System.out.println("Max accepted per client: " + summary.maxAcceptedAcrossClients());
        System.out.println("Consistent (heuristic):  " + summary.consistentClientDistribution());
    }
}
