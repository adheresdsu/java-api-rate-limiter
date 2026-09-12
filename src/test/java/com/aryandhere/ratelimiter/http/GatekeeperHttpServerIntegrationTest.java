package com.aryandhere.ratelimiter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.core.FixedWindowRateLimiter;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.time.SystemTimeSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that start the real {@link GatekeeperHttpServer} on an
 * ephemeral port and drive it with a real {@link HttpClient}. Every request
 * uses a bounded timeout so a failure cannot hang the build.
 */
class GatekeeperHttpServerIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private GatekeeperHttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private GatekeeperHttpServer startServer(RateLimiter rateLimiter) throws IOException {
        server = GatekeeperHttpServer.start(0, 4, rateLimiter);
        return server;
    }

    private String baseUrl() {
        return "http://localhost:" + server.port();
    }

    private HttpResponse<String> get(String path, String clientIdHeaderValue, boolean includeHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (includeHeader) {
            builder.header("X-Client-Id", clientIdHeaderValue);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReturnsOkAndIsNeverRateLimited() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = get("/health", null, false);
            assertEquals(200, response.statusCode());
            assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
            assertEquals("{\"status\":\"ok\"}", response.body());
        }
    }

    @Test
    void healthRejectsUnsupportedMethod() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/health"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void missingClientIdReturns400() throws Exception {
        startServer(new FixedWindowRateLimiter(2, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpResponse<String> response = get("/api/resource", null, false);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    void blankClientIdReturns400() throws Exception {
        startServer(new FixedWindowRateLimiter(2, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpResponse<String> response = get("/api/resource", "   ", true);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    void allowedRequestReturns200WithRateLimitHeaders() throws Exception {
        startServer(new FixedWindowRateLimiter(2, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpResponse<String> response = get("/api/resource", "client-1", true);

        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("client-1"));
        assertEquals("2", response.headers().firstValue("X-RateLimit-Limit").orElse(""));
        assertEquals("1", response.headers().firstValue("X-RateLimit-Remaining").orElse(""));
        assertTrue(response.headers().firstValue("Retry-After").isEmpty());
    }

    @Test
    void requestBeyondLimitReturns429WithRetryAfter() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpResponse<String> first = get("/api/resource", "client-1", true);
        HttpResponse<String> second = get("/api/resource", "client-1", true);

        assertEquals(200, first.statusCode());
        assertEquals(429, second.statusCode());
        assertTrue(second.body().contains("error"));
        assertEquals("0", second.headers().firstValue("X-RateLimit-Remaining").orElse(""));

        String retryAfter = second.headers().firstValue("Retry-After").orElseThrow();
        long retryAfterSeconds = Long.parseLong(retryAfter);
        assertTrue(retryAfterSeconds >= 0);
    }

    @Test
    void remainingNeverGoesNegativeAcrossManyRejectedRequests() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        get("/api/resource", "client-1", true);
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = get("/api/resource", "client-1", true);
            assertEquals(429, response.statusCode());
            assertEquals("0", response.headers().firstValue("X-RateLimit-Remaining").orElse(""));
        }
    }

    @Test
    void twoClientsHaveIndependentLimits() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpResponse<String> clientA = get("/api/resource", "client-a", true);
        HttpResponse<String> clientAAgain = get("/api/resource", "client-a", true);
        HttpResponse<String> clientB = get("/api/resource", "client-b", true);

        assertEquals(200, clientA.statusCode());
        assertEquals(429, clientAAgain.statusCode());
        assertEquals(200, clientB.statusCode());
    }

    @Test
    void resourceRejectsUnsupportedMethod() throws Exception {
        startServer(new FixedWindowRateLimiter(2, Duration.ofSeconds(60), new SystemTimeSource()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/resource"))
                .timeout(REQUEST_TIMEOUT)
                .header("X-Client-Id", "client-1")
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void concurrentRequestsNeverExceedConfiguredCapacity() throws Exception {
        int limit = 20;
        int threadCount = 100;
        startServer(new FixedWindowRateLimiter(limit, Duration.ofSeconds(60), new SystemTimeSource()));

        AtomicInteger acceptedCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        HttpResponse<String> response = get("/api/resource", "shared-client", true);
                        if (response.statusCode() == 200) {
                            acceptedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertEquals(limit, acceptedCount.get());
    }

    @Test
    void serverStartsOnEphemeralPortAndStopsCleanly() throws Exception {
        startServer(new FixedWindowRateLimiter(1, Duration.ofSeconds(60), new SystemTimeSource()));

        assertTrue(server.port() > 0);
        HttpResponse<String> response = get("/health", null, false);
        assertEquals(200, response.statusCode());

        String stoppedServerUrl = baseUrl();
        server.stop();
        server = null; // already stopped; avoid double-stop in @AfterEach

        assertTrue(isConnectionRefused(stoppedServerUrl));
    }

    private boolean isConnectionRefused(String url) {
        try {
            client.send(
                    HttpRequest.newBuilder().uri(URI.create(url + "/health")).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return false;
        } catch (IOException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
