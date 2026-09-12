package com.aryandhere.ratelimiter.simulation;

import java.net.URI;

/**
 * Parses command-line arguments into a {@link SimulatorConfig}.
 *
 * <p>Every recognized flag takes exactly one value: {@code --flag value}.
 * Unknown flags, missing values, and invalid or unsafe values are all
 * rejected with a {@link SimulatorConfigException} carrying a short,
 * specific message; {@link #usage()} explains the full set of options.
 */
public final class SimulatorArgsParser {

    private SimulatorArgsParser() {
    }

    public static String usage() {
        return """
                Usage: traffic-simulator [options]
                  --base-url <url>                 target Gatekeeper server, localhost only (default %s)
                  --client-count <int>              number of simulated clients (default %d, max %d)
                  --requests-per-client <int>       measured requests per client (default %d, max %d)
                  --concurrency <int>               worker threads (default %d, max %d)
                  --request-timeout-seconds <int>   per-request timeout (default %d)
                  --client-id-prefix <text>         prefix for X-Client-Id values (default %s)
                  --warmup-requests <int>           unmeasured warmup requests (default %d, max %d)
                """.formatted(
                SimulatorConfig.DEFAULT_BASE_URL,
                SimulatorConfig.DEFAULT_CLIENT_COUNT, SimulatorConfig.MAX_CLIENT_COUNT,
                SimulatorConfig.DEFAULT_REQUESTS_PER_CLIENT, SimulatorConfig.MAX_REQUESTS_PER_CLIENT,
                SimulatorConfig.DEFAULT_CONCURRENCY, SimulatorConfig.MAX_CONCURRENCY,
                SimulatorConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS,
                SimulatorConfig.DEFAULT_CLIENT_ID_PREFIX,
                SimulatorConfig.DEFAULT_WARMUP_REQUESTS, SimulatorConfig.MAX_WARMUP_REQUESTS);
    }

    public static SimulatorConfig parse(String[] args) throws SimulatorConfigException {
        String baseUrl = SimulatorConfig.DEFAULT_BASE_URL;
        int clientCount = SimulatorConfig.DEFAULT_CLIENT_COUNT;
        int requestsPerClient = SimulatorConfig.DEFAULT_REQUESTS_PER_CLIENT;
        int concurrency = SimulatorConfig.DEFAULT_CONCURRENCY;
        int requestTimeoutSeconds = SimulatorConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS;
        String clientIdPrefix = SimulatorConfig.DEFAULT_CLIENT_ID_PREFIX;
        int warmupRequests = SimulatorConfig.DEFAULT_WARMUP_REQUESTS;

        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            String value = requireValue(args, i, flag);
            i += 2;

            switch (flag) {
                case "--base-url" -> baseUrl = value;
                case "--client-count" -> clientCount = parseInt(flag, value);
                case "--requests-per-client" -> requestsPerClient = parseInt(flag, value);
                case "--concurrency" -> concurrency = parseInt(flag, value);
                case "--request-timeout-seconds" -> requestTimeoutSeconds = parseInt(flag, value);
                case "--client-id-prefix" -> clientIdPrefix = value;
                case "--warmup-requests" -> warmupRequests = parseInt(flag, value);
                default -> throw new SimulatorConfigException("Unknown argument '" + flag + "'");
            }
        }

        URI baseUri = LocalTargetValidator.requireLocalHttpBaseUri(baseUrl);

        try {
            return new SimulatorConfig(baseUri, clientCount, requestsPerClient, concurrency,
                    requestTimeoutSeconds, clientIdPrefix, warmupRequests);
        } catch (IllegalArgumentException e) {
            throw new SimulatorConfigException(e.getMessage());
        }
    }

    private static String requireValue(String[] args, int index, String flag) throws SimulatorConfigException {
        if (index + 1 >= args.length) {
            throw new SimulatorConfigException("Missing value for argument '" + flag + "'");
        }
        return args[index + 1];
    }

    private static int parseInt(String flag, String value) throws SimulatorConfigException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new SimulatorConfigException("Argument '" + flag + "' must be an integer, got '" + value + "'");
        }
    }
}
