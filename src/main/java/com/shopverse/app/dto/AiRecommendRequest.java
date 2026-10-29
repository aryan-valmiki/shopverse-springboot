package com.shopverse.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendRequest {
    private String category;    // e.g. "Laptops", "Smartphones"
    private String ram;         // e.g. "16GB", "8GB"
    private String processor;   // e.g. "Intel Core i7", "AMD Ryzen 5"
    private String storage;     // e.g. "512GB SSD", "1TB SSD"
    private Double maxBudget;   // e.g. 70000.0
    private String usage;       // e.g. "Coding, College Project, Gaming"
    private String query;       // Additional notes or requirements
}
