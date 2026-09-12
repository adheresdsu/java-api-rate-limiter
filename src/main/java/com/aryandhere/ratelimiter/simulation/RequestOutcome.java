package com.aryandhere.ratelimiter.simulation;

/** The category every measured or warmup request falls into, exactly one each. */
public enum RequestOutcome {
    ALLOWED,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    TRANSPORT_ERROR
}
