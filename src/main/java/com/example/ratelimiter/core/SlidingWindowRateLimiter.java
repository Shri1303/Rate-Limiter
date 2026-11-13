package com.example.ratelimiter.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis-backed Sliding Window Counter. All read-check-increment logic lives in a single
 * Lua script executed atomically on the Redis server, so N concurrent app instances share
 * one consistent view with no race conditions and a single network round trip per check.
 */
@Service
public class SlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;

    @SuppressWarnings("rawtypes")
    public SlidingWindowRateLimiter(StringRedisTemplate redis, DefaultRedisScript<List> slidingWindowScript) {
        this.redis = redis;
        this.script = slidingWindowScript;
    }

    @Override
    public RateLimitDecision check(String key, long limit, long windowSeconds) {
        long windowMs = windowSeconds * 1000;
        long nowMs = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                    script,
                    List.of(KEY_PREFIX + key),
                    String.valueOf(limit),
                    String.valueOf(windowMs),
                    String.valueOf(nowMs));

            if (result == null || result.size() < 3) {
                log.warn("Unexpected script result for key {}: {}", key, result);
                return failOpen(limit);
            }
            boolean allowed = result.get(0) == 1L;
            return new RateLimitDecision(allowed, limit, result.get(1), result.get(2));
        } catch (Exception e) {
            // Fail open: a Redis outage should degrade protection, not take the API down.
            log.error("Rate limiter backend unavailable, allowing request for key {}", key, e);
            return failOpen(limit);
        }
    }

    private static RateLimitDecision failOpen(long limit) {
        return new RateLimitDecision(true, limit, limit, 0);
    }
}
