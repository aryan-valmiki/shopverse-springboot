package com.shopverse.app.exceptions;

import java.io.IOException;

public class InvalidIoException extends RuntimeException {
    public InvalidIoException(String message) {
        super(message);
    }
}
