package com.example.ratelimiter.web;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RateLimitProperties.Rule;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Resolves a throttling key for each request (API token if present, else client IP),
 * looks up the matching rule, and rejects with 429 when the limit is exceeded.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties props) {
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (!props.isEnabled()) return true;

        String token = req.getHeader(props.getTokenHeader());
        String key;
        Rule rule;

        if (token != null && !token.isBlank()) {
            key = "token:" + token;
            rule = props.getTokenRules().getOrDefault(token, props.getDefaultRule());
        } else {
            key = "ip:" + clientIp(req);
            rule = props.getDefaultRule();
        }

        RateLimitDecision d = rateLimiter.check(key, rule.limit(), rule.windowSeconds());

        res.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(d.remaining()));

        if (d.allowed()) return true;

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("""
                {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Retry after %d seconds.","retryAfterSeconds":%d}
                """.formatted(d.retryAfterSeconds(), d.retryAfterSeconds()));
        return false;
    }

    /** Honors X-Forwarded-For when behind a proxy/load balancer; falls back to the socket address. */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
