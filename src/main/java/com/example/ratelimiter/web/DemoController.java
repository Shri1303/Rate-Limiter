package com.example.ratelimiter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Stand-in for a protected downstream service. */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("message", "pong", "timestamp", Instant.now().toString());
    }
}
