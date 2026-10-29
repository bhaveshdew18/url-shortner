package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.model.Click;
import com.bhavesh.urlshortner.repository.ClickRepository;
import com.bhavesh.urlshortner.repository.UrlRepository;
import com.bhavesh.urlshortner.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


import java.net.URI;
import java.time.Instant;
import java.util.Locale;

@Service
public class ClickService {

    private static final Logger log = LoggerFactory.getLogger(ClickService.class);

    private final ClickRepository clicks;
    private final UrlRepository urls;
    private final TransactionTemplate tx;

    public ClickService(ClickRepository clicks, UrlRepository urls, TransactionTemplate tx) {
        this.clicks = clicks;
        this.urls = urls;
        this.tx = tx;
    }

    @Async("clickExecutor")
    public void record(long urlId, Instant clickedAt, String referer, String userAgent) {
        try {
            String host = hostOf(referer);
            tx.executeWithoutResult(status -> {
                clicks.save(new Click(urlId, clickedAt, host, userAgent));
                urls.incrementClickCount(urlId);
            });
            log.debug("Recorded click for url {}", urlId);
        } catch (RuntimeException e) {
            log.warn("Failed to record click for url {}", urlId, e);
        }
    }

    private static String hostOf(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            String host = URI.create(referer).getHost();
            return host == null ? null : Text.clip(host.toLowerCase(Locale.ROOT), 255);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
