package com.aryandhere.ratelimiter;

import com.aryandhere.ratelimiter.config.ArgsParser;
import com.aryandhere.ratelimiter.config.ConfigException;
import com.aryandhere.ratelimiter.config.RateLimiterFactory;
import com.aryandhere.ratelimiter.config.ServerConfig;
import com.aryandhere.ratelimiter.core.RateLimiter;
import com.aryandhere.ratelimiter.http.GatekeeperHttpServer;
import com.aryandhere.ratelimiter.time.SystemTimeSource;
import java.io.IOException;

/** Command-line entry point: parses configuration, starts the server, and waits to be stopped. */
public final class GatekeeperApplication {

    private GatekeeperApplication() {
    }

    public static void main(String[] args) {
        ServerConfig config = parseConfigOrExit(args);
        RateLimiter rateLimiter = RateLimiterFactory.create(config, new SystemTimeSource());
        GatekeeperHttpServer server = startServerOrExit(config, rateLimiter);

        System.out.println("Gatekeeper listening on http://localhost:" + server.port());
        System.out.println("Algorithm: " + config.algorithm().cliName());
        System.out.println("Workers: " + config.workers());
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.stop();
        }, "gatekeeper-shutdown"));
    }

    private static ServerConfig parseConfigOrExit(String[] args) {
        try {
            return ArgsParser.parse(args);
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.print(ArgsParser.usage());
            System.exit(1);
            throw new AssertionError("unreachable");
        }
    }

    private static GatekeeperHttpServer startServerOrExit(ServerConfig config, RateLimiter rateLimiter) {
        try {
            return GatekeeperHttpServer.start(config.port(), config.workers(), rateLimiter);
        } catch (IOException e) {
            System.err.println("Error: failed to start server: " + e.getMessage());
            System.exit(1);
            throw new AssertionError("unreachable");
        }
    }
}
