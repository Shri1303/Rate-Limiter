package com.example.ratelimiter.core;

/**
 * Result of a single rate-limit check.
 *
 * @param allowed      whether the request may proceed
 * @param limit        the configured ceiling for this key
 * @param remaining    requests left in the current sliding window
 * @param retryAfterMs milliseconds until a request would next be admitted (0 if allowed)
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, long retryAfterMs) {

    public long retryAfterSeconds() {
        return (long) Math.ceil(retryAfterMs / 1000.0);
    }
}
