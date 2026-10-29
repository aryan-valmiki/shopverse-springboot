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
public class AiVerdictDto {
    private String winnerTitle;
    private Integer winnerProductId;
    private String summary;
    private List<String> product1Pros;
    private List<String> product2Pros;
    private String product1BestFor;
    private String product2BestFor;
}
