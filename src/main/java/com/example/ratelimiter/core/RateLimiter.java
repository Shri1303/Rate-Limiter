package com.example.ratelimiter.core;

public interface RateLimiter {
    /**
     * @param key           identity being throttled (already namespaced, e.g. "ip:1.2.3.4")
     * @param limit         max requests per window
     * @param windowSeconds window length
     */
    RateLimitDecision check(String key, long limit, long windowSeconds);
}
