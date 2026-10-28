package com.bhavesh.urlshortner.exception;

import org.springframework.http.HttpStatus;

public class AliasAlreadyExistsException extends ApiException{
    public AliasAlreadyExistsException(String alias) {
        super(HttpStatus.CONFLICT, "Alias '" + alias + "' is already in use");
    }
}
