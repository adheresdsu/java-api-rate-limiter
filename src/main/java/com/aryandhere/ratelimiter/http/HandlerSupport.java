package com.aryandhere.ratelimiter.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/**
 * Wraps a handler action so every route gets the same unexpected-error and
 * resource-cleanup behavior without repeating it in each handler.
 */
final class HandlerSupport {

    private HandlerSupport() {
    }

    @FunctionalInterface
    interface Action {
        void run(HttpExchange exchange) throws IOException;
    }

    /**
     * Runs {@code action}, converting any unexpected exception into a 500
     * response that carries no stack trace or internal detail, and always
     * closes the exchange afterwards.
     */
    static void safely(HttpExchange exchange, Action action) throws IOException {
        try {
            action.run(exchange);
        } catch (Exception e) {
            System.err.println("Unhandled error handling " + exchange.getRequestURI() + ": " + e);
            JsonSupport.write(exchange, 500, "{\"error\":\"Internal server error\"}");
        } finally {
            exchange.close();
        }
    }
}
