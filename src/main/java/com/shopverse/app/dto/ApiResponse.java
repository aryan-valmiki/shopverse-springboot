package com.shopverse.app.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@Data
public class ApiResponse<T> {
    private String message;
    private Boolean success;
    private T data;

    public ApiResponse(String message, Boolean success, T data){
        this.message = message;
        this.success = success;
        this.data = data;
    }
}
