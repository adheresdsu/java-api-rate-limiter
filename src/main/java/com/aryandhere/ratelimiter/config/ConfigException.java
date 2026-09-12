package com.aryandhere.ratelimiter.config;

/** Thrown when command-line configuration is missing, malformed, or out of range. */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }
}
