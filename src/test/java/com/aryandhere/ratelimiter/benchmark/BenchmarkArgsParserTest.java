package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BenchmarkArgsParserTest {

    @Test
    void usesDefaultsWhenNoArgsGiven() throws BenchmarkConfigException {
        BenchmarkConfig config = BenchmarkArgsParser.parse(new String[0]);

        assertEquals(BenchmarkConfig.defaults(), config);
    }

    @Test
    void parsesAllSupportedFlags() throws BenchmarkConfigException {
        BenchmarkConfig config = BenchmarkArgsParser.parse(new String[] {
                "--clients", "20",
                "--operations-per-client", "50",
                "--concurrency", "2",
                "--warmup-operations", "100",
                "--trials", "5"
        });

        assertEquals(20, config.clients());
        assertEquals(50, config.operationsPerClient());
        assertEquals(2, config.concurrency());
        assertEquals(100, config.warmupOperations());
        assertEquals(5, config.trials());
    }

    @Test
    void rejectsUnknownArgument() {
        assertThrows(BenchmarkConfigException.class, () -> BenchmarkArgsParser.parse(new String[] {"--bogus", "1"}));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(BenchmarkConfigException.class, () -> BenchmarkArgsParser.parse(new String[] {"--clients"}));
    }

    @Test
    void rejectsNonNumericValue() {
        assertThrows(BenchmarkConfigException.class,
                () -> BenchmarkArgsParser.parse(new String[] {"--clients", "abc"}));
    }

    @Test
    void rejectsNonPositiveClients() {
        assertThrows(BenchmarkConfigException.class,
                () -> BenchmarkArgsParser.parse(new String[] {"--clients", "0"}));
    }

    @Test
    void rejectsTrialsBelowMinimum() {
        assertThrows(BenchmarkConfigException.class,
                () -> BenchmarkArgsParser.parse(new String[] {"--trials", "2"}));
    }

    @Test
    void rejectsTrialsAboveMaximum() {
        assertThrows(BenchmarkConfigException.class, () -> BenchmarkArgsParser.parse(
                new String[] {"--trials", String.valueOf(BenchmarkConfig.MAX_TRIALS + 1)}));
    }

    @Test
    void rejectsExcessiveTotalOperations() {
        assertThrows(BenchmarkConfigException.class, () -> BenchmarkArgsParser.parse(new String[] {
                "--clients", "100000",
                "--operations-per-client", "1000000"
        }));
    }

    @Test
    void usageMentionsEveryFlag() {
        String usage = BenchmarkArgsParser.usage();

        assertTrue(usage.contains("--clients"));
        assertTrue(usage.contains("--operations-per-client"));
        assertTrue(usage.contains("--concurrency"));
        assertTrue(usage.contains("--warmup-operations"));
        assertTrue(usage.contains("--trials"));
    }
}
