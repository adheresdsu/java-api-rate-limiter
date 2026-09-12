package com.aryandhere.ratelimiter.config;

/**
 * Parses command-line arguments into a {@link ServerConfig}.
 *
 * <p>Every recognized flag takes exactly one value: {@code --flag value}.
 * Unknown flags, missing values, and out-of-range or non-numeric values are
 * all rejected with a {@link ConfigException} carrying a short, specific
 * message; {@link #usage()} explains the full set of options.
 */
public final class ArgsParser {

    private ArgsParser() {
    }

    public static String usage() {
        return """
                Usage: gatekeeper [options]
                  --port <int>                    TCP port to listen on (default %d)
                  --algorithm <name>               fixed-window | sliding-window | token-bucket (default %s)
                  --limit <int>                    requests per window, fixed-window/sliding-window (default %d)
                  --window-seconds <int>            window length in seconds, fixed-window/sliding-window (default %d)
                  --capacity <int>                  bucket capacity, token-bucket (default %d)
                  --refill-tokens <number>          tokens per refill period, token-bucket (default %s)
                  --refill-period-seconds <number>  refill period in seconds, token-bucket (default %s)
                  --workers <int>                   HTTP server thread pool size (default %d)
                """.formatted(
                ServerConfig.DEFAULT_PORT,
                ServerConfig.DEFAULT_ALGORITHM.cliName(),
                ServerConfig.DEFAULT_LIMIT,
                ServerConfig.DEFAULT_WINDOW_SECONDS,
                ServerConfig.DEFAULT_CAPACITY,
                ServerConfig.DEFAULT_REFILL_TOKENS,
                ServerConfig.DEFAULT_REFILL_PERIOD_SECONDS,
                ServerConfig.DEFAULT_WORKERS);
    }

    public static ServerConfig parse(String[] args) throws ConfigException {
        ServerConfig defaults = ServerConfig.defaults();
        int port = defaults.port();
        AlgorithmType algorithm = defaults.algorithm();
        int limit = defaults.limit();
        long windowSeconds = defaults.windowSeconds();
        long capacity = defaults.capacity();
        double refillTokens = defaults.refillTokens();
        double refillPeriodSeconds = defaults.refillPeriodSeconds();
        int workers = defaults.workers();

        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            String value = requireValue(args, i, flag);
            i += 2;

            switch (flag) {
                case "--port" -> port = parseInt(flag, value);
                case "--algorithm" -> algorithm = parseAlgorithm(value);
                case "--limit" -> limit = parseInt(flag, value);
                case "--window-seconds" -> windowSeconds = parseLong(flag, value);
                case "--capacity" -> capacity = parseLong(flag, value);
                case "--refill-tokens" -> refillTokens = parseDouble(flag, value);
                case "--refill-period-seconds" -> refillPeriodSeconds = parseDouble(flag, value);
                case "--workers" -> workers = parseInt(flag, value);
                default -> throw new ConfigException("Unknown argument '" + flag + "'");
            }
        }

        try {
            return new ServerConfig(port, algorithm, limit, windowSeconds, capacity, refillTokens,
                    refillPeriodSeconds, workers);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage());
        }
    }

    private static String requireValue(String[] args, int index, String flag) throws ConfigException {
        if (index + 1 >= args.length) {
            throw new ConfigException("Missing value for argument '" + flag + "'");
        }
        return args[index + 1];
    }

    private static AlgorithmType parseAlgorithm(String value) throws ConfigException {
        try {
            return AlgorithmType.fromCliName(value);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage());
        }
    }

    private static int parseInt(String flag, String value) throws ConfigException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigException("Argument '" + flag + "' must be an integer, got '" + value + "'");
        }
    }

    private static long parseLong(String flag, String value) throws ConfigException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ConfigException("Argument '" + flag + "' must be an integer, got '" + value + "'");
        }
    }

    private static double parseDouble(String flag, String value) throws ConfigException {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new NumberFormatException(value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ConfigException("Argument '" + flag + "' must be a number, got '" + value + "'");
        }
    }
}
