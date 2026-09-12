package com.aryandhere.ratelimiter.http;

import com.aryandhere.ratelimiter.core.RateLimitDecision;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 * Serves {@code GET /api/resource}, the rate-limited demonstration endpoint.
 *
 * <p>Every request must carry an {@code X-Client-Id} header (not a query
 * parameter). The limiter is consulted exactly once per request via
 * {@link RateLimiter#decide(String)}, and the resulting {@link
 * RateLimitDecision} drives both the response headers and the response
 * status, so the decision is never made twice for the same request.
 */
public final class ResourceHandler implements HttpHandler {

    private static final String CLIENT_ID_HEADER = "X-Client-Id";

    private final RateLimiter rateLimiter;

    public ResourceHandler(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HandlerSupport.safely(exchange, this::doHandle);
    }

    private void doHandle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            JsonSupport.write(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String clientId = exchange.getRequestHeaders().getFirst(CLIENT_ID_HEADER);
        if (clientId == null) {
            JsonSupport.write(exchange, 400, "{\"error\":\"Missing X-Client-Id header\"}");
            return;
        }
        if (clientId.isBlank()) {
            JsonSupport.write(exchange, 400, "{\"error\":\"X-Client-Id header must not be blank\"}");
            return;
        }

        RateLimitDecision decision = rateLimiter.decide(clientId);
        applyRateLimitHeaders(exchange.getResponseHeaders(), decision);

        if (decision.allowed()) {
            String body = "{\"message\":\"Access granted\",\"clientId\":\"" + JsonSupport.escape(clientId) + "\"}";
            JsonSupport.write(exchange, 200, body);
        } else {
            JsonSupport.write(exchange, 429, "{\"error\":\"Rate limit exceeded\"}");
        }
    }

    private void applyRateLimitHeaders(Headers responseHeaders, RateLimitDecision decision) {
        responseHeaders.set("X-RateLimit-Limit", Long.toString(decision.limit()));
        responseHeaders.set("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (decision.retryAfter() != null) {
            responseHeaders.set("Retry-After", Long.toString(decision.retryAfter().toSeconds()));
        }
    }
}
