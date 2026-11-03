package com.bhavesh.urlshortner.controller;

import com.bhavesh.urlshortner.dto.ApiError;
import com.bhavesh.urlshortner.service.ClickService;
import com.bhavesh.urlshortner.service.LinkTarget;
import com.bhavesh.urlshortner.service.UrlService;
import com.bhavesh.urlshortner.util.Text;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@RestController
public class RedirectController {

    private final UrlService service;
    private final ClickService clickService;
    private final Clock clock;

    public RedirectController(UrlService service, ClickService clickService, Clock clock) {
        this.service = service;
        this.clickService = clickService;
        this.clock = clock;
    }

    @Operation(summary = "Follow a short link",
            description = "Responds with 302 and a Location header. Records a click asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the original URL"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "410", description = "The link has expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referer,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        LinkTarget target = service.resolve(code);

        clickService.record(target.id(), Instant.now(clock),
                Text.clip(referer, 512), Text.clip(userAgent, 512));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target.longUrl()))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}