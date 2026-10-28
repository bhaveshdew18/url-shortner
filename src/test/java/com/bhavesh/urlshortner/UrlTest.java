package com.bhavesh.urlshortner;

import com.bhavesh.urlshortner.model.Url;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlTest {
    private final Instant now = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void neverExpiresWhenNoExpiry() {
        assertFalse(new Url("https://e.com", null).isExpired(now));
    }
    @Test void expiredWhenInThePast() {
        assertTrue(new Url("https://e.com", now.minusSeconds(1)).isExpired(now));
    }
    @Test void expiredExactlyAtBoundary() {
        assertTrue(new Url("https://e.com", now).isExpired(now));
    }
}
