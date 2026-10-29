package com.shopverse.app.controllers;

import com.shopverse.app.dto.*;
import com.shopverse.app.services.AiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    // Public AI Product Recommendation endpoint
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<AiRecommendResponse>> getRecommendations(
            @RequestBody(required = false) AiRecommendRequest request
    ) {
        if (request == null) {
            request = new AiRecommendRequest();
        }

        AiRecommendResponse response = aiService.getRecommendations(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>("AI recommendations fetched successfully", true, response));
    }

    // Public AI Side-by-Side Product Comparison & Verdict endpoint
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<AiCompareResponse>> compareProducts(
            @RequestBody AiCompareRequest request
    ) {
        AiCompareResponse response = aiService.compareProducts(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>("AI comparison verdict generated successfully", true, response));
    }

    // Public Natural Language Voice / Prompt-to-Cart endpoint
    @PostMapping("/prompt-search")
    public ResponseEntity<ApiResponse<AiPromptSearchResponse>> promptSearch(
            @RequestBody AiPromptSearchRequest request
    ) {
        AiPromptSearchResponse response = aiService.promptSearch(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>("Prompt search matches generated", true, response));
    }
}
