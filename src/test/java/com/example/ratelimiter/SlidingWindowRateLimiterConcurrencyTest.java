package com.example.ratelimiter;

import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires a running Redis on localhost:6379 (docker compose up redis).
 * Fires many concurrent requests at one key and asserts that exactly `limit`
 * are admitted — proving the Lua script eliminates the check-then-increment race.
 */
@SpringBootTest
class SlidingWindowRateLimiterConcurrencyTest {

    @Autowired RateLimiter rateLimiter;
    @Autowired StringRedisTemplate redis;

    private static final String KEY = "test:concurrency";

    @BeforeEach
    void clean() {
        var keys = redis.keys("rl:" + KEY + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void admitsExactlyLimitUnderConcurrentLoad() throws Exception {
        int limit = 50;
        int attempts = 500;
        int threads = 32;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<Long>> latencies = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            latencies.add(pool.submit(() -> {
                start.await();
                long t0 = System.nanoTime();
                RateLimitDecision d = rateLimiter.check(KEY, limit, 60);
                long elapsed = System.nanoTime() - t0;
                if (d.allowed()) allowed.incrementAndGet();
                return elapsed;
            }));
        }

        start.countDown();
        long maxNanos = 0, totalNanos = 0;
        for (Future<Long> f : latencies) {
            long n = f.get(10, TimeUnit.SECONDS);
            totalNanos += n;
            maxNanos = Math.max(maxNanos, n);
        }
        pool.shutdown();

        System.out.printf("avg=%.2fms max=%.2fms%n", totalNanos / 1e6 / attempts, maxNanos / 1e6);
        assertThat(allowed.get()).isEqualTo(limit);
    }

    @Test
    void returnsRetryAfterWhenBlocked() {
        for (int i = 0; i < 3; i++) rateLimiter.check(KEY + ":small", 3, 60);
        RateLimitDecision d = rateLimiter.check(KEY + ":small", 3, 60);

        assertThat(d.allowed()).isFalse();
        assertThat(d.remaining()).isZero();
        assertThat(d.retryAfterMs()).isGreaterThan(0);
    }
}
