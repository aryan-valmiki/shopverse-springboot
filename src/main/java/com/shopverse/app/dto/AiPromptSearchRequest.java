package com.shopverse.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptSearchRequest {
    private String prompt; // e.g. "I want a 16GB RAM laptop for programming under 80000"
}
