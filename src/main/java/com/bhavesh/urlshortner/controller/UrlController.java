package com.bhavesh.urlshortner.controller;

import com.bhavesh.urlshortner.dto.CreateUrlRequest;
import com.bhavesh.urlshortner.dto.CreateUrlResponse;
import com.bhavesh.urlshortner.dto.UrlStatsResponse;
import com.bhavesh.urlshortner.service.StatsService;
import com.bhavesh.urlshortner.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService service;
    private final StatsService statsService;

    public UrlController(UrlService service, StatsService statsService) {
        this.service = service;
        this.statsService = statsService;
    }

    @PostMapping
    public ResponseEntity<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{code}/stats")
    public UrlStatsResponse stats(@PathVariable String code) {
        return statsService.stats(code);
    }
}
