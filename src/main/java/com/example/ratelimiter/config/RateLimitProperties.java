package com.example.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds rate-limit rules from application.yml.
 */
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Rule defaultRule = new Rule(100, 60);
    /** Per-API-token overrides keyed by the raw token value. */
    private Map<String, Rule> tokenRules = Map.of();
    /** Header carrying the API token. */
    private String tokenHeader = "X-API-Key";

    public record Rule(long limit, long windowSeconds) {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Rule getDefaultRule() { return defaultRule; }
    public void setDefaultRule(Rule defaultRule) { this.defaultRule = defaultRule; }
    public Map<String, Rule> getTokenRules() { return tokenRules; }
    public void setTokenRules(Map<String, Rule> tokenRules) { this.tokenRules = tokenRules; }
    public String getTokenHeader() { return tokenHeader; }
    public void setTokenHeader(String tokenHeader) { this.tokenHeader = tokenHeader; }
}
