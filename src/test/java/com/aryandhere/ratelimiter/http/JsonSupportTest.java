package com.aryandhere.ratelimiter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonSupportTest {

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertEquals("hello-world_123", JsonSupport.escape("hello-world_123"));
    }

    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("a\\\"b\\\\c", JsonSupport.escape("a\"b\\c"));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("a\\nb\\tc", JsonSupport.escape("a\nb\tc"));
    }

    @Test
    void preventsJsonInjectionViaClientSuppliedValue() {
        String malicious = "x\",\"injected\":true,\"y\":\"";

        String escaped = JsonSupport.escape(malicious);
        String body = "{\"clientId\":\"" + escaped + "\"}";

        // The escaped value must not introduce a new, unescaped '"' that would close the string early.
        assertEquals("{\"clientId\":\"x\\\",\\\"injected\\\":true,\\\"y\\\":\\\"\"}", body);
    }
}
