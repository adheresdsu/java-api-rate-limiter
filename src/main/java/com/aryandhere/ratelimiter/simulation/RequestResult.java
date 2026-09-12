package com.aryandhere.ratelimiter.simulation;

/**
 * The outcome of one simulated request.
 *
 * @param clientIndex which simulated client (0-based) made the request
 * @param outcome the result category
 * @param latencyNanos wall time from just before the request was sent to
 *     just after it completed (successfully or not), measured with {@link
 *     System#nanoTime()}
 */
record RequestResult(int clientIndex, RequestOutcome outcome, long latencyNanos) {
}
