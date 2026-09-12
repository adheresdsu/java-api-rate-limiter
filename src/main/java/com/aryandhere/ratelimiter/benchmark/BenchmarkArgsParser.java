package com.aryandhere.ratelimiter.benchmark;

/**
 * Parses command-line arguments into a {@link BenchmarkConfig}.
 *
 * <p>Every recognized flag takes exactly one value: {@code --flag value}.
 * Unknown flags, missing values, and invalid or unsafe values are all
 * rejected with a {@link BenchmarkConfigException} carrying a short,
 * specific message; {@link #usage()} explains the full set of options.
 */
public final class BenchmarkArgsParser {

    private BenchmarkArgsParser() {
    }

    public static String usage() {
        return """
                Usage: algorithm-benchmark [options]
                  --clients <int>                  deterministic client identifiers (default %d, max %d)
                  --operations-per-client <int>     measured decide() calls per client, per trial (default %d, max %d)
                  --concurrency <int>               worker threads (default %d, max %d)
                  --warmup-operations <int>         unmeasured operations before each trial (default %d, max %d)
                  --trials <int>                    measured trials per algorithm (default %d, min %d, max %d)
                """.formatted(
                BenchmarkConfig.DEFAULT_CLIENTS, BenchmarkConfig.MAX_CLIENTS,
                BenchmarkConfig.DEFAULT_OPERATIONS_PER_CLIENT, BenchmarkConfig.MAX_OPERATIONS_PER_CLIENT,
                BenchmarkConfig.DEFAULT_CONCURRENCY, BenchmarkConfig.MAX_CONCURRENCY,
                BenchmarkConfig.DEFAULT_WARMUP_OPERATIONS, BenchmarkConfig.MAX_WARMUP_OPERATIONS,
                BenchmarkConfig.DEFAULT_TRIALS, BenchmarkConfig.MINIMUM_TRIALS_FOR_MEDIAN, BenchmarkConfig.MAX_TRIALS);
    }

    public static BenchmarkConfig parse(String[] args) throws BenchmarkConfigException {
        int clients = BenchmarkConfig.DEFAULT_CLIENTS;
        int operationsPerClient = BenchmarkConfig.DEFAULT_OPERATIONS_PER_CLIENT;
        int concurrency = BenchmarkConfig.DEFAULT_CONCURRENCY;
        int warmupOperations = BenchmarkConfig.DEFAULT_WARMUP_OPERATIONS;
        int trials = BenchmarkConfig.DEFAULT_TRIALS;

        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            String value = requireValue(args, i, flag);
            i += 2;

            switch (flag) {
                case "--clients" -> clients = parseInt(flag, value);
                case "--operations-per-client" -> operationsPerClient = parseInt(flag, value);
                case "--concurrency" -> concurrency = parseInt(flag, value);
                case "--warmup-operations" -> warmupOperations = parseInt(flag, value);
                case "--trials" -> trials = parseInt(flag, value);
                default -> throw new BenchmarkConfigException("Unknown argument '" + flag + "'");
            }
        }

        try {
            return new BenchmarkConfig(clients, operationsPerClient, concurrency, warmupOperations, trials);
        } catch (IllegalArgumentException e) {
            throw new BenchmarkConfigException(e.getMessage());
        }
    }

    private static String requireValue(String[] args, int index, String flag) throws BenchmarkConfigException {
        if (index + 1 >= args.length) {
            throw new BenchmarkConfigException("Missing value for argument '" + flag + "'");
        }
        return args[index + 1];
    }

    private static int parseInt(String flag, String value) throws BenchmarkConfigException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BenchmarkConfigException("Argument '" + flag + "' must be an integer, got '" + value + "'");
        }
    }
}
