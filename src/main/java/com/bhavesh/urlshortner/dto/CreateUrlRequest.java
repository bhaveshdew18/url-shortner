package com.bhavesh.urlshortner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(
        @Schema(description = "The destination (http or https only)",
                example = "https://example.com/some/long/path?x=1")
        @NotBlank @Size(max = 2048) String longUrl,

        @Schema(description = "Optional custom alias: 3-32 chars of letters, digits, '-' or '_', "
                + "at least one '-' or '_'", example = "summer-sale")
        @Size(max = 32) String customAlias,

        @Schema(description = "Optional expiry, must be in the future", example = "2030-12-31T23:59:59Z")
        @Future Instant expiresAt
) {}