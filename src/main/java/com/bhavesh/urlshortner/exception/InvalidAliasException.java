package com.bhavesh.urlshortner.exception;

import org.springframework.http.HttpStatus;

public class InvalidAliasException extends ApiException {
    public InvalidAliasException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
