package com.bhavesh.urlshortner.exception;


import org.springframework.http.HttpStatus;

public class UrlNotFoundException extends ApiException{
    public UrlNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND,"No URL found for code: " + code);
    }
}
