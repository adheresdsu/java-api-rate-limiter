package com.aryandhere.ratelimiter.http;

import com.aryandhere.ratelimiter.core.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Wraps the JDK's {@link HttpServer} with the Gatekeeper routes, a bounded
 * worker thread pool, and graceful shutdown.
 *
 * <p>One {@link RateLimiter} instance and one {@link ExecutorService} are
 * created at startup and shared by every request; neither is recreated per
 * request.
 */
public final class GatekeeperHttpServer {

    private static final int STOP_DELAY_SECONDS = 1;
    private static final int EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 5;

    private final HttpServer httpServer;
    private final ExecutorService executor;

    private GatekeeperHttpServer(HttpServer httpServer, ExecutorService executor) {
        this.httpServer = httpServer;
        this.executor = executor;
    }

    /**
     * Builds and starts the server, bound to {@code port} (use {@code 0} for
     * an OS-assigned ephemeral port, useful in tests).
     */
    public static GatekeeperHttpServer start(int port, int workers, RateLimiter rateLimiter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        server.setExecutor(executor);
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/resource", new ResourceHandler(rateLimiter));
        server.start();
        return new GatekeeperHttpServer(server, executor);
    }

    /** The port actually bound, useful when started with {@code port == 0}. */
    public int port() {
        return httpServer.getAddress().getPort();
    }

    /**
     * Stops accepting new connections, gives in-flight requests up to {@value
     * #STOP_DELAY_SECONDS} second(s) to finish, then shuts down the worker
     * pool: gracefully first, forcing termination only if it does not
     * complete within {@value #EXECUTOR_TERMINATION_TIMEOUT_SECONDS} seconds.
     */
    public void stop() {
        httpServer.stop(STOP_DELAY_SECONDS);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
