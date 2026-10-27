package com.bhavesh.urlshortner.dto;

import java.time.Instant;

public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant expiresAt
) {}