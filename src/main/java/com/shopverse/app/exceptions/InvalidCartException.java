package com.shopverse.app.exceptions;

public class InvalidCartException extends RuntimeException{
    public InvalidCartException(String message) {
        super(message);
    }
}
