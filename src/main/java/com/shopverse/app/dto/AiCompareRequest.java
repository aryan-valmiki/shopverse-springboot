package com.shopverse.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCompareRequest {
    private Integer product1Id;
    private Integer product2Id;
    private String preference; // e.g. "Coding", "Gaming", "Everyday", "Budget"
}
