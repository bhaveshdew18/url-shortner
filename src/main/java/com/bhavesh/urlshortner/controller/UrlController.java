package com.bhavesh.urlshortner.controller;

import com.bhavesh.urlshortner.dto.ApiError;
import com.bhavesh.urlshortner.dto.CreateUrlRequest;
import com.bhavesh.urlshortner.dto.CreateUrlResponse;
import com.bhavesh.urlshortner.dto.UrlStatsResponse;
import com.bhavesh.urlshortner.service.StatsService;
import com.bhavesh.urlshortner.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Links", description = "Create short links and read their statistics")
public class UrlController {

    private final UrlService service;
    private final StatsService statsService;

    public UrlController(UrlService service, StatsService statsService) {
        this.service = service;
        this.statsService = statsService;
    }

    @PostMapping
    @Operation(summary = "Create a short link",
            description = "Optionally choose a custom alias (3-32 chars, must contain '-' or '_') "
                    + "and an expiry date. Rate limited per client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Link created"),
            @ApiResponse(responseCode = "400", description = "Invalid URL, alias, expiry or JSON",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already taken",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded; see the Retry-After header",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{code}/stats")
    @Operation(summary = "Get statistics for a short link",
            description = "Total clicks, clicks in the last 24 hours and top referrer hosts. "
                    + "Counts are eventually consistent because clicks are recorded asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UrlStatsResponse stats(@PathVariable String code) {
        return statsService.stats(code);
    }
}