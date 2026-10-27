package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.dto.CreateUrlRequest;
import com.bhavesh.urlshortner.dto.CreateUrlResponse;
import com.bhavesh.urlshortner.exception.UrlNotFoundException;
import com.bhavesh.urlshortner.model.Url;
import com.bhavesh.urlshortner.repository.UrlRepository;
import com.bhavesh.urlshortner.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlService {

    private final UrlRepository repository;
    private final String baseUrl;

    public UrlService(UrlRepository repository,
                      @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request) {

        Url url = repository.save(new Url(request.longUrl(), request.expiresAt()));
        url.setShortCode(Base62.encode(url.getId()));
        return new CreateUrlResponse(
                url.getShortCode(),
                baseUrl + "/" +url.getShortCode(),
                url.getLongUrl(),
                url.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        return repository.findByShortCode(code)
                .map(Url::getLongUrl)
                .orElseThrow(() -> new UrlNotFoundException(code));
    }

}
