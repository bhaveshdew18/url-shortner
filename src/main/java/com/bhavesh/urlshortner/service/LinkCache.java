package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class LinkCache {

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);
    private static final String PREFIX = "url:";
    private static final String NEGATIVE_MARKER = "-";

    public enum Kind { HIT, NEGATIVE, MISS }

    public record Lookup(Kind kind, LinkTarget target) {
        static final Lookup MISS = new Lookup(Kind.MISS, null);
        static final Lookup NEGATIVE = new Lookup(Kind.NEGATIVE, null);
    }

    private final StringRedisTemplate redis;
    private final CacheProperties props;
    private final Clock clock;

    public LinkCache(StringRedisTemplate redis, CacheProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    public Lookup get(String code) {
        try {
            String raw = redis.opsForValue().get(PREFIX + code);
            if (raw == null) return Lookup.MISS;
            if (raw.equals(NEGATIVE_MARKER)) return Lookup.NEGATIVE;
            return LinkTarget.fromCacheValue(raw)
                    .map(target -> new Lookup(Kind.HIT, target))
                    .orElseGet(() -> {
                        log.warn("Corrupt cache entry for '{}', ignoring it", code);
                        return Lookup.MISS;
                    });
        } catch (RuntimeException e) {
            log.warn("Redis unavailable on get, falling back to the database: {}", e.toString());
            return Lookup.MISS;
        }
    }

    public void put(String code, LinkTarget target) {
        Duration ttl = props.linkTtl();
        if (target.expiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(clock), target.expiresAt());
            if (remaining.toMillis() < 1) return;               // already expired: don't cache
            if (remaining.compareTo(ttl) < 0) ttl = remaining;   // never outlive the link
        }
        set(code, target.toCacheValue(), ttl);
    }

    public void putNegative(String code) {
        set(code, NEGATIVE_MARKER, props.negativeTtl());
    }

    public void evict(String code) {
        try {
            redis.delete(PREFIX + code);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable on evict: {}", e.toString());
        }
    }

    private void set(String code, String value, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + code, value, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable on set: {}", e.toString());
        }
    }
}