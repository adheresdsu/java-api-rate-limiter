package com.aryandhere.ratelimiter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArgsParserTest {

    @Test
    void usesDefaultsWhenNoArgsGiven() throws ConfigException {
        ServerConfig config = ArgsParser.parse(new String[0]);

        assertEquals(ServerConfig.defaults(), config);
    }

    @Test
    void parsesAllSupportedFlags() throws ConfigException {
        ServerConfig config = ArgsParser.parse(new String[] {
                "--port", "9090",
                "--algorithm", "fixed-window",
                "--limit", "50",
                "--window-seconds", "30",
                "--capacity", "10",
                "--refill-tokens", "5",
                "--refill-period-seconds", "2",
                "--workers", "4"
        });

        assertEquals(9090, config.port());
        assertEquals(AlgorithmType.FIXED_WINDOW, config.algorithm());
        assertEquals(50, config.limit());
        assertEquals(30, config.windowSeconds());
        assertEquals(10, config.capacity());
        assertEquals(5.0, config.refillTokens());
        assertEquals(2.0, config.refillPeriodSeconds());
        assertEquals(4, config.workers());
    }

    @Test
    void rejectsUnknownArgument() {
        ConfigException exception = assertThrows(ConfigException.class,
                () -> ArgsParser.parse(new String[] {"--bogus", "1"}));
        assertTrue(exception.getMessage().contains("--bogus"));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(ConfigException.class, () -> ArgsParser.parse(new String[] {"--port"}));
    }

    @Test
    void rejectsNonNumericPort() {
        assertThrows(ConfigException.class, () -> ArgsParser.parse(new String[] {"--port", "abc"}));
    }

    @Test
    void rejectsOutOfRangePort() {
        assertThrows(ConfigException.class, () -> ArgsParser.parse(new String[] {"--port", "70000"}));
    }

    @Test
    void rejectsUnknownAlgorithm() {
        assertThrows(ConfigException.class, () -> ArgsParser.parse(new String[] {"--algorithm", "round-robin"}));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(ConfigException.class, () -> ArgsParser.parse(new String[] {"--limit", "0"}));
    }

    @Test
    void usageMentionsEveryFlag() {
        String usage = ArgsParser.usage();

        assertTrue(usage.contains("--port"));
        assertTrue(usage.contains("--algorithm"));
        assertTrue(usage.contains("--limit"));
        assertTrue(usage.contains("--window-seconds"));
        assertTrue(usage.contains("--capacity"));
        assertTrue(usage.contains("--refill-tokens"));
        assertTrue(usage.contains("--refill-period-seconds"));
        assertTrue(usage.contains("--workers"));
    }
}
