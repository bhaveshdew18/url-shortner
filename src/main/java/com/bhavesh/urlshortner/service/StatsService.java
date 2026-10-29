package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.dto.ReferrerCount;
import com.bhavesh.urlshortner.dto.UrlStatsResponse;
import com.bhavesh.urlshortner.exception.UrlNotFoundException;
import com.bhavesh.urlshortner.model.Url;
import com.bhavesh.urlshortner.repository.ClickRepository;
import com.bhavesh.urlshortner.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class StatsService {

    private final UrlRepository urls;
    private final ClickRepository clicks;
    private final LinkPolicy policy;
    private final Clock clock;

    public StatsService(UrlRepository urls, ClickRepository clicks, LinkPolicy policy, Clock clock) {
        this.urls = urls;
        this.clicks = clicks;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse stats(String code) {
        if (!policy.isValidCodeFormat(code)) {
            throw new UrlNotFoundException(code);
        }
        Url url = urls.findByShortCode(code).orElseThrow(() -> new UrlNotFoundException(code));

        long last24h = clicks.countByUrlIdAndClickedAtAfter(
                url.getId(), Instant.now(clock).minus(Duration.ofHours(24)));
        List<ReferrerCount> top = clicks.topReferrers(url.getId()).stream()
                .map(r -> new ReferrerCount(r.getReferrer(), r.getClicks()))
                .toList();

        return new UrlStatsResponse(url.getShortCode(), url.getLongUrl(), url.getClickCount(),
                last24h, top, url.getCreatedAt(), url.getExpiresAt());
    }
}
