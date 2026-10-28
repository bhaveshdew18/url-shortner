package com.bhavesh.urlshortner.exception;

import org.springframework.http.HttpStatus;

public class UrlExpiredException extends ApiException {
    public UrlExpiredException(String code) {
        super(HttpStatus.GONE, "The link has expired");
    }
}
