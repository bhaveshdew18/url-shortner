package com.bhavesh.urlshortner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("10") long capacity,
        @DefaultValue("1m") Duration refillPeriod,
        @DefaultValue("10000") long maxTrackedClients,
        @DefaultValue("10m") Duration clientIdleTtl) {}