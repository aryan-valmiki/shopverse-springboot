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
public class AiCompareResponse {
    private Product product1;
    private Product product2;
    private AiVerdictDto verdict;
}
