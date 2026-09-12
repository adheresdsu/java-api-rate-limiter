package com.aryandhere.ratelimiter.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/** Serves {@code GET /health}. Never rate limited. */
public final class HealthHandler implements HttpHandler {

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
        JsonSupport.write(exchange, 200, "{\"status\":\"ok\"}");
    }
}
