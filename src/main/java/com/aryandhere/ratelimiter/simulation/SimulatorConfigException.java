package com.aryandhere.ratelimiter.simulation;

/** Thrown when traffic simulator command-line configuration is missing, malformed, or unsafe. */
public final class SimulatorConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    public SimulatorConfigException(String message) {
        super(message);
    }
}
