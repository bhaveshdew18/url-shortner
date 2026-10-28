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

import java.time.Clock;
import java.time.Instant;

@Service
public class UrlService {

    private final UrlRepository repository;
    private final LinkPolicy policy;
    private final Clock clock;
    private final String baseUrl;

    public UrlService(UrlRepository repository, LinkPolicy policy, Clock clock,
                      @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.policy = policy;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");   // tolerate a trailing slash in config
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
        return new CreateUrlResponse(url.getShortCode(), baseUrl + "/" + url.getShortCode(),
                url.getLongUrl(), url.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        Url url = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        if (url.isExpired(Instant.now(clock))) {
            throw new UrlExpiredException(code);
        }
        return url.getLongUrl();
    }
}


