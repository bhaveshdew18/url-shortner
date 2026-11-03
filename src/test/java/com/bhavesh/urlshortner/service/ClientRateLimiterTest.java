package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRateLimiterTest {

    // capacity 3, refills only 3 per HOUR: no sleeping needed, the 4th call is refused
    private final ClientRateLimiter limiter = new ClientRateLimiter(
            new RateLimitProperties(3, Duration.ofHours(1), 100, Duration.ofHours(2)));

    @Test
    void allowsUpToCapacityThenRejects() {
        assertTrue(limiter.tryAcquire("1.1.1.1").allowed());
        assertTrue(limiter.tryAcquire("1.1.1.1").allowed());
        assertTrue(limiter.tryAcquire("1.1.1.1").allowed());

        ClientRateLimiter.Decision denied = limiter.tryAcquire("1.1.1.1");
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
        assertTrue(denied.retryAfterSeconds() > 0 && denied.retryAfterSeconds() <= 3600);
    }

    @Test
    void remainingTokensCountDown() {
        assertEquals(2, limiter.tryAcquire("2.2.2.2").remaining());
        assertEquals(1, limiter.tryAcquire("2.2.2.2").remaining());
        assertEquals(0, limiter.tryAcquire("2.2.2.2").remaining());
    }

    @Test
    void clientsAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("3.3.3.3");
        }
        assertFalse(limiter.tryAcquire("3.3.3.3").allowed());
        assertTrue(limiter.tryAcquire("4.4.4.4").allowed());
    }
}