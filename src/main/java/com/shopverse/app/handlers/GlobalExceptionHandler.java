package com.shopverse.app.handlers;

import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<ApiResponse<String>> handleUserException(InvalidUserException ex){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<String>(ex.getMessage(), false, "No data"));
    }

    @ExceptionHandler(InvalidProductException.class)
    public ResponseEntity<ApiResponse<String>> handleProductException(InvalidProductException ex){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<String>(ex.getMessage(), false, "No data"));
    }

    @ExceptionHandler(InvalidCartException.class)
    public ResponseEntity<ApiResponse<String>> handleCartException(InvalidCartException ex){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(ex.getMessage(), false, "No data"));
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ApiResponse<String>> handleOrderException(InvalidOrderException ex){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(ex.getMessage(), false, "No data"));
    }

    @ExceptionHandler(InvalidIoException.class)
    public ResponseEntity<ApiResponse<String>> handleIoException(InvalidIoException ex){
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(ex.getMessage(), false, "No data"));
    }
}
