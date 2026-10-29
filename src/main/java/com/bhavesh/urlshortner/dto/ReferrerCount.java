package com.bhavesh.urlshortner.dto;

public record ReferrerCount(
        String referrer,
        Long clicks
) {
}
