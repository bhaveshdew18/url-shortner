package com.bhavesh.urlshortner.dto;

import java.time.Instant;
import java.util.List;

public record UrlStatsResponse(
        String shortCode,
        String longUrl,
        long clickCount,
        long clicksLast24Hours,
        List<ReferrerCount> topReferrers,
        Instant createdAt,
        Instant expiresAt) {}
