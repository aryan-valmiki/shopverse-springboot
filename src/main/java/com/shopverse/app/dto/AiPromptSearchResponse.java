package com.shopverse.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptSearchResponse {
    private String detectedIntent; // e.g. "Looking for Laptops with 16GB RAM for Coding under ₹80,000"
    private List<AiRecommendationItem> matches;
}
