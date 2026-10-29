package com.shopverse.app.dto;

import com.shopverse.app.models.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendationItem {
    private Integer productId;
    private Product product;
    private Integer matchScore;
    private String matchReason;
    private String highlight;
}
