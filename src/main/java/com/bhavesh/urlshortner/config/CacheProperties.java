package com.bhavesh.urlshortner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        @DefaultValue("1h") Duration linkTtl,
        @DefaultValue("30s") Duration negativeTtl) {}
