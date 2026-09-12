package com.aryandhere.ratelimiter.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulatorArgsParserTest {

    @Test
    void usesDefaultsWhenNoArgsGiven() throws SimulatorConfigException {
        SimulatorConfig config = SimulatorArgsParser.parse(new String[0]);

        assertEquals(SimulatorConfig.DEFAULT_BASE_URL, config.baseUri().toString());
        assertEquals(SimulatorConfig.DEFAULT_CLIENT_COUNT, config.clientCount());
        assertEquals(SimulatorConfig.DEFAULT_REQUESTS_PER_CLIENT, config.requestsPerClient());
        assertEquals(SimulatorConfig.DEFAULT_CONCURRENCY, config.concurrency());
        assertEquals(SimulatorConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS, config.requestTimeoutSeconds());
        assertEquals(SimulatorConfig.DEFAULT_CLIENT_ID_PREFIX, config.clientIdPrefix());
        assertEquals(SimulatorConfig.DEFAULT_WARMUP_REQUESTS, config.warmupRequests());
    }

    @Test
    void parsesAllSupportedFlags() throws SimulatorConfigException {
        SimulatorConfig config = SimulatorArgsParser.parse(new String[] {
                "--base-url", "http://127.0.0.1:9090",
                "--client-count", "5",
                "--requests-per-client", "10",
                "--concurrency", "4",
                "--request-timeout-seconds", "3",
                "--client-id-prefix", "load-test",
                "--warmup-requests", "20"
        });

        assertEquals("http://127.0.0.1:9090", config.baseUri().toString());
        assertEquals(5, config.clientCount());
        assertEquals(10, config.requestsPerClient());
        assertEquals(4, config.concurrency());
        assertEquals(3, config.requestTimeoutSeconds());
        assertEquals("load-test", config.clientIdPrefix());
        assertEquals(20, config.warmupRequests());
    }

    @Test
    void rejectsUnknownArgument() {
        assertThrows(SimulatorConfigException.class, () -> SimulatorArgsParser.parse(new String[] {"--bogus", "1"}));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--client-count"}));
    }

    @Test
    void rejectsNonNumericClientCount() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--client-count", "abc"}));
    }

    @Test
    void rejectsNonPositiveClientCount() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--client-count", "0"}));
    }

    @Test
    void rejectsNonPositiveRequestsPerClient() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--requests-per-client", "-1"}));
    }

    @Test
    void rejectsNonPositiveConcurrency() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--concurrency", "0"}));
    }

    @Test
    void rejectsConcurrencyAboveSafeMaximum() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--concurrency",
                        String.valueOf(SimulatorConfig.MAX_CONCURRENCY + 1)}));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--request-timeout-seconds", "0"}));
    }

    @Test
    void rejectsRemoteBaseUrl() {
        assertThrows(SimulatorConfigException.class,
                () -> SimulatorArgsParser.parse(new String[] {"--base-url", "http://example.com:8080"}));
    }

    @Test
    void rejectsExcessiveTotalMeasuredRequests() {
        assertThrows(SimulatorConfigException.class, () -> SimulatorArgsParser.parse(new String[] {
                "--client-count", "10000",
                "--requests-per-client", "100000"
        }));
    }

    @Test
    void usageMentionsEveryFlag() {
        String usage = SimulatorArgsParser.usage();

        assertTrue(usage.contains("--base-url"));
        assertTrue(usage.contains("--client-count"));
        assertTrue(usage.contains("--requests-per-client"));
        assertTrue(usage.contains("--concurrency"));
        assertTrue(usage.contains("--request-timeout-seconds"));
        assertTrue(usage.contains("--client-id-prefix"));
        assertTrue(usage.contains("--warmup-requests"));
    }
}
