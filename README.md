# Distributed API Rate Limiter & Throttling Service

A standalone rate limiter built with **Java 17, Spring Boot 3, Redis, and Docker** that protects
downstream services from traffic spikes using the **Sliding Window Counter** algorithm.

## Features

- **Sliding Window Counter** — two fixed-window counters per key, weighted by overlap. Smooths the
  burst-at-boundary problem of fixed windows while using O(1) memory per key (vs. O(N) for a sliding log).
- **Atomic Redis Lua script** — read, estimate, compare, and increment happen in one server-side
  command, so concurrent requests across many app instances cannot race past the limit.
- **Dynamic key-based throttling** — throttles per API token (`X-API-Key`) when present, otherwise
  per client IP (honors `X-Forwarded-For`). Per-token limit overrides via config.
- **Standard HTTP semantics** — `429 Too Many Requests` with `Retry-After`, plus
  `X-RateLimit-Limit` / `X-RateLimit-Remaining` headers on every response.
- **Fail-open** — if Redis is unreachable, requests are allowed and the error is logged, so the
  limiter never becomes the outage.

## Project layout

```
src/main/java/com/example/ratelimiter/
├── RateLimiterApplication.java
├── config/
│   ├── RateLimitProperties.java   # ratelimit.* config binding
│   ├── RedisConfig.java           # StringRedisTemplate + cached Lua script bean
│   └── WebConfig.java             # registers the interceptor on /api/**
├── core/
│   ├── RateLimiter.java
│   ├── RateLimitDecision.java
│   └── SlidingWindowRateLimiter.java
└── web/
    ├── RateLimitInterceptor.java  # key resolution + 429 handling
    └── DemoController.java        # protected sample endpoint
src/main/resources/scripts/sliding_window.lua
```

## Run

```bash
docker compose up --build
```

```bash
# Default (per-IP) rule: 100 req / 60 s
curl -i http://localhost:8080/api/ping

# Trial token: 10 req / 60 s — the 11th call returns 429
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: demo-trial-key" http://localhost:8080/api/ping
done
```

Sample 429 response:

```
HTTP/1.1 429
Retry-After: 42
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0

{"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Retry after 42 seconds.","retryAfterSeconds":42}
```

## Tests

`SlidingWindowRateLimiterConcurrencyTest` fires 500 concurrent checks from 32 threads at one key
with a limit of 50 and asserts exactly 50 are admitted. It also prints average/max decision latency.

```bash
docker compose up -d redis
mvn test
```

## Load testing

```bash
# e.g. with hey (https://github.com/rakyll/hey)
hey -z 30s -c 100 -H "X-API-Key: demo-premium-key" http://localhost:8080/api/ping
```

## How the algorithm works

For a window of `W` ms at time `now`:

```
curr   = count in current fixed window
prev   = count in previous fixed window
weight = (W - (now mod W)) / W        # how much of prev still overlaps
estimated = prev * weight + curr
allow iff estimated < limit, then INCR curr
```

Keys expire after `2W`, so memory stays bounded automatically.

## Possible extensions

- Load per-token rules from a database or config server with hot reload
- Token-bucket mode for burst allowances
- Redis Cluster hash tags (`rl:{token}:…`) to keep both window keys on one shard
- Expose a `/limits/{key}` admin endpoint and Micrometer counters for allowed/blocked
