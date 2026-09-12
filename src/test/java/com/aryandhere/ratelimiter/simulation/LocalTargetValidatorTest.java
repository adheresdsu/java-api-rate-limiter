package com.aryandhere.ratelimiter.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LocalTargetValidatorTest {

    @Test
    void acceptsLocalhost() throws SimulatorConfigException {
        URI uri = LocalTargetValidator.requireLocalHttpBaseUri("http://localhost:8080");
        assertEquals("localhost", uri.getHost());
    }

    @Test
    void acceptsLoopbackIpv4() throws SimulatorConfigException {
        URI uri = LocalTargetValidator.requireLocalHttpBaseUri("http://127.0.0.1:8080");
        assertEquals("127.0.0.1", uri.getHost());
    }

    @Test
    void acceptsLoopbackIpv6() throws SimulatorConfigException {
        LocalTargetValidator.requireLocalHttpBaseUri("http://[::1]:8080");
    }

    @Test
    void rejectsRemoteHost() {
        SimulatorConfigException e = assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://example.com:8080"));
        assertEquals(true, e.getMessage().contains("localhost"));
    }

    @Test
    void rejectsHttpsScheme() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("https://localhost:8080"));
    }

    @Test
    void rejectsUserInfo() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://user:pass@localhost:8080"));
    }

    @Test
    void rejectsQueryString() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://localhost:8080?inject=1"));
    }

    @Test
    void rejectsFragment() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://localhost:8080#frag"));
    }

    @Test
    void rejectsPath() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://localhost:8080/admin"));
    }

    @Test
    void rejectsMalformedUri() {
        assertThrows(SimulatorConfigException.class,
                () -> LocalTargetValidator.requireLocalHttpBaseUri("http://loc alhost:8080"));
    }
}
