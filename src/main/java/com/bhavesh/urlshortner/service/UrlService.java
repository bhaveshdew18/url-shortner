package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.dto.CreateUrlRequest;
import com.bhavesh.urlshortner.dto.CreateUrlResponse;
import com.bhavesh.urlshortner.exception.AliasAlreadyExistsException;
import com.bhavesh.urlshortner.exception.UrlExpiredException;
import com.bhavesh.urlshortner.exception.UrlNotFoundException;
import com.bhavesh.urlshortner.model.Url;
import com.bhavesh.urlshortner.repository.UrlRepository;
import com.bhavesh.urlshortner.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class UrlService {

    private final UrlRepository repository;
    private final LinkPolicy policy;
    private final Clock clock;
    private final String baseUrl;
    private final LinkCache cache;

    public UrlService(UrlRepository repository, LinkPolicy policy, LinkCache cache,
                      Clock clock, @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.policy = policy;
        this.cache = cache;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public LinkTarget resolve(String code) {
        if (!policy.isValidCodeFormat(code)) {
            throw new UrlNotFoundException(code);
        }

        LinkCache.Lookup cached = cache.get(code);
        if (cached.kind() == LinkCache.Kind.HIT) {
            return requireLive(code, cached.target());
        }
        if (cached.kind() == LinkCache.Kind.NEGATIVE) {
            throw new UrlNotFoundException(code);
        }

        // cache miss: go to the database
        Optional<Url> row = repository.findByShortCode(code);
        if (row.isEmpty()) {
            cache.putNegative(code);
            throw new UrlNotFoundException(code);
        }
        Url url = row.get();
        LinkTarget target = new LinkTarget(url.getId(), url.getLongUrl(), url.getExpiresAt());
        cache.put(code, target);
        return requireLive(code, target);
    }

    private LinkTarget requireLive(String code, LinkTarget target) {
        if (target.isExpired(Instant.now(clock))) {
            throw new UrlExpiredException(code);
        }
        return target;
    }

    private void evictAfterCommit(String code) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(code);
            }
        });
    }

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request) {
        String longUrl = request.longUrl().trim();
        policy.validateLongUrl(longUrl);

        Url url = new Url(longUrl, request.expiresAt());
        String alias = request.customAlias();

        if (alias != null) {
            policy.validateAlias(alias);
            if (repository.existsByShortCode(alias)) {
                throw new AliasAlreadyExistsException(alias);
            }
            url.setShortCode(alias);
            try {
                repository.save(url);
            } catch (DataIntegrityViolationException e) {
                // lost a race with a concurrent request: the unique index is the real guard
                throw new AliasAlreadyExistsException(alias);
            }
        } else {
            repository.save(url);
            url.setShortCode(Base62.encode(url.getId()));
        }
        evictAfterCommit(url.getShortCode());
        return new CreateUrlResponse(url.getShortCode(), baseUrl + "/" + url.getShortCode(),
                url.getLongUrl(), url.getExpiresAt());
    }
}


