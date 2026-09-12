package com.aryandhere.ratelimiter.config;

/** Thrown when command-line configuration is missing, malformed, or out of range. */
public final class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
