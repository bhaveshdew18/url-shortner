package com.bhavesh.urlshortner.service;

import java.time.Instant;
import java.util.Optional;

public record LinkTarget(long id, String longUrl, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Format: id|expiresAtEpochMillis(-1 = never)|longUrl */
    String toCacheValue() {
        return id + "|" + (expiresAt == null ? -1 : expiresAt.toEpochMilli()) + "|" + longUrl;
    }

    static Optional<LinkTarget> fromCacheValue(String raw) {
        int first = raw.indexOf('|');
        int second = first < 0 ? -1 : raw.indexOf('|', first + 1);
        if (second < 0) return Optional.empty();
        try {
            long id = Long.parseLong(raw.substring(0, first));
            long expiry = Long.parseLong(raw.substring(first + 1, second));
            String url = raw.substring(second + 1);
            if (url.isEmpty()) return Optional.empty();
            return Optional.of(new LinkTarget(id, url, expiry < 0 ? null : Instant.ofEpochMilli(expiry)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
