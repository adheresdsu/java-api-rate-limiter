package com.aryandhere.ratelimiter.simulation;

/** Thrown when traffic simulator command-line configuration is missing, malformed, or unsafe. */
public final class SimulatorConfigException extends Exception {

    public SimulatorConfigException(String message) {
        super(message);
    }
}
