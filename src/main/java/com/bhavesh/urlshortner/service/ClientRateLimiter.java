package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ClientRateLimiter {

    public record Decision(boolean allowed, long limit, long remaining, long retryAfterSeconds) {}

    private final RateLimitProperties props;
    private final Cache<String, Bucket> buckets;

    public ClientRateLimiter( RateLimitProperties props) {
        this.props = props;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(props.maxTrackedClients())      // never grow without limit
                .expireAfterAccess(props.clientIdleTtl())    // forget clients that went quiet
                .build();
    }

    public Decision tryAcquire(String clientKey) {
        Bucket bucket = buckets.get(clientKey, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return new Decision(true, props.capacity(), probe.getRemainingTokens(), 0);
        }
        long nanos = probe.getNanosToWaitForRefill();
        long retryAfter = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);   // round up
        return new Decision(false, props.capacity(), 0, retryAfter);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(props.capacity())
                        .refillGreedy(props.capacity(), props.refillPeriod()))
                .build();
    }
}