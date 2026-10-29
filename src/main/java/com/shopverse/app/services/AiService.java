package com.shopverse.app.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopverse.app.dto.*;
import com.shopverse.app.exceptions.InvalidProductException;
import com.shopverse.app.models.Product;
import com.shopverse.app.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiService(ProductRepository productRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AiRecommendResponse getRecommendations(AiRecommendRequest request) {
        List<Product> allProducts = productRepository.findAll();

        if (allProducts.isEmpty()) {
            return AiRecommendResponse.builder()
                    .summary("No products are currently available in the ShopVerse catalog. Please add products from the admin panel first.")
                    .recommendations(Collections.emptyList())
                    .build();
        }

        // Try getting intelligent recommendations from Groq AI
        try {
            return callGroqForRecommendations(request, allProducts);
        } catch (Exception ex) {
            System.err.println("Groq AI Call failed, using smart fallback: " + ex.getMessage());
            return fallbackRuleBasedRecommendation(request, allProducts);
        }
    }

    private AiRecommendResponse callGroqForRecommendations(AiRecommendRequest request, List<Product> products) throws Exception {
        // Build product catalog text to feed the AI
        StringBuilder catalogBuilder = new StringBuilder();
        for (Product p : products) {
            catalogBuilder.append(String.format(
                    "- Product ID: %d | Name: %s | Price: ₹%.0f | Category: %s | Brand: %s | RAM: %s | Processor: %s | Storage: %s | Description: %s\n",
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    p.getCategory() != null ? p.getCategory() : "General",
                    p.getBrand() != null ? p.getBrand() : "Generic",
                    p.getRam() != null ? p.getRam() : "N/A",
                    p.getProcessor() != null ? p.getProcessor() : "N/A",
                    p.getStorage() != null ? p.getStorage() : "N/A",
                    p.getDescription() != null ? p.getDescription().replaceAll("\n", " ") : ""
            ));
        }

        String systemPrompt = "You are an AI Product Advisor for ShopVerse, an e-commerce platform. " +
                "You must recommend products to the user based SOLELY on the products available in the store inventory provided below. " +
                "NEVER invent or recommend products that are not present in the inventory list. " +
                "Return your response strictly as a JSON object with this exact structure: " +
                "{\"summary\": \"A helpful 1-2 sentence overall summary of your recommendation\", " +
                "\"recommendations\": [{\"productId\": 1, \"matchScore\": 95, \"matchReason\": \"Detailed explanation why this matches their requested specs like RAM and processor\", \"highlight\": \"Key highlight badge like 16GB RAM + Core i7\"}]}";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("User's Desired Specifications:\n");
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            userPrompt.append("- Category: ").append(request.getCategory()).append("\n");
        }
        if (request.getRam() != null && !request.getRam().isBlank()) {
            userPrompt.append("- Required RAM: ").append(request.getRam()).append("\n");
        }
        if (request.getProcessor() != null && !request.getProcessor().isBlank()) {
            userPrompt.append("- Required Processor: ").append(request.getProcessor()).append("\n");
        }
        if (request.getStorage() != null && !request.getStorage().isBlank()) {
            userPrompt.append("- Required Storage: ").append(request.getStorage()).append("\n");
        }
        if (request.getMaxBudget() != null && request.getMaxBudget() > 0) {
            userPrompt.append("- Maximum Budget: ₹").append(request.getMaxBudget()).append("\n");
        }
        if (request.getUsage() != null && !request.getUsage().isBlank()) {
            userPrompt.append("- Intended Use: ").append(request.getUsage()).append("\n");
        }
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            userPrompt.append("- Additional Preferences: ").append(request.getQuery()).append("\n");
        }

        userPrompt.append("\nAVAILABLE PRODUCTS IN SHOPVERSE INVENTORY:\n");
        userPrompt.append(catalogBuilder);
        userPrompt.append("\nPlease select up to 4 best matching products from the inventory. Prioritize products matching the requested RAM and Processor. Return strictly valid JSON.");

        // Construct request body for Groq (OpenAI-compatible format)
        Map<String, Object> messageSystem = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> messageUser = Map.of("role", "user", "content", userPrompt.toString());

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", groqModel);
        requestBodyMap.put("messages", List.of(messageSystem, messageUser));
        requestBodyMap.put("temperature", 0.3);
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String requestJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(httpResponse.body());
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String aiContent = choices.get(0).path("message").path("content").asText();
                return parseAiJsonResponse(aiContent, products);
            }
        }

        throw new RuntimeException("Groq API returned status: " + httpResponse.statusCode() + " body: " + httpResponse.body());
    }

    private AiRecommendResponse parseAiJsonResponse(String aiContent, List<Product> allProducts) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiContent);
            String summary = jsonNode.path("summary").asText("Here are the best matching products from our catalog based on your specifications.");

            Map<Integer, Product> productMap = allProducts.stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            List<AiRecommendationItem> recommendations = new ArrayList<>();
            JsonNode recNodes = jsonNode.path("recommendations");

            if (recNodes.isArray()) {
                for (JsonNode item : recNodes) {
                    int productId = item.path("productId").asInt();
                    Product p = productMap.get(productId);

                    if (p != null) {
                        recommendations.add(AiRecommendationItem.builder()
                                .productId(productId)
                                .product(p)
                                .matchScore(item.path("matchScore").asInt(90))
                                .matchReason(item.path("matchReason").asText("Matches your desired hardware specifications."))
                                .highlight(item.path("highlight").asText("Best Match"))
                                .build());
                    }
                }
            }

            if (!recommendations.isEmpty()) {
                return AiRecommendResponse.builder()
                        .summary(summary)
                        .recommendations(recommendations)
                        .build();
            }
        } catch (Exception e) {
            System.err.println("Error parsing AI JSON response: " + e.getMessage());
        }

        return fallbackRuleBasedRecommendation(new AiRecommendRequest(), allProducts);
    }

    private AiRecommendResponse fallbackRuleBasedRecommendation(AiRecommendRequest req, List<Product> products) {
        String reqRam = req.getRam() != null ? req.getRam().toLowerCase() : "";
        String reqProc = req.getProcessor() != null ? req.getProcessor().toLowerCase() : "";
        String reqCat = req.getCategory() != null ? req.getCategory().toLowerCase() : "";
        Double maxBudget = req.getMaxBudget();

        List<AiRecommendationItem> items = new ArrayList<>();

        for (Product p : products) {
            int score = 60;
            List<String> highlights = new ArrayList<>();

            if (!reqCat.isBlank() && p.getCategory() != null && p.getCategory().toLowerCase().contains(reqCat)) {
                score += 15;
                highlights.add(p.getCategory());
            }

            if (!reqRam.isBlank()) {
                String pRam = p.getRam() != null ? p.getRam().toLowerCase() : "";
                String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                if (pRam.contains(reqRam) || pDesc.contains(reqRam)) {
                    score += 20;
                    highlights.add("RAM Match: " + (p.getRam() != null ? p.getRam() : req.getRam()));
                }
            }

            if (!reqProc.isBlank()) {
                String pProc = p.getProcessor() != null ? p.getProcessor().toLowerCase() : "";
                String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                if (pProc.contains(reqProc) || pDesc.contains(reqProc)) {
                    score += 20;
                    highlights.add("Processor Match: " + (p.getProcessor() != null ? p.getProcessor() : req.getProcessor()));
                }
            }

            if (maxBudget != null && maxBudget > 0 && p.getPrice() != null) {
                if (p.getPrice() <= maxBudget) {
                    score += 10;
                    highlights.add("Within Budget");
                }
            }

            score = Math.min(score, 99);

            String reason = "Recommended based on your hardware preferences: ";
            if (!highlights.isEmpty()) {
                reason += String.join(", ", highlights) + ".";
            } else {
                reason += "Popular choice in our inventory matching your category.";
            }

            items.add(AiRecommendationItem.builder()
                    .productId(p.getId())
                    .product(p)
                    .matchScore(score)
                    .matchReason(reason)
                    .highlight(!highlights.isEmpty() ? highlights.get(0) : "Recommended Pick")
                    .build());
        }

        items.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        List<AiRecommendationItem> topItems = items.stream().limit(4).collect(Collectors.toList());

        return AiRecommendResponse.builder()
                .summary("Based on your desired specifications, here are the top matching products available in ShopVerse.")
                .recommendations(topItems)
                .build();
    }

    // =========================================================================
    // AI SIDE-BY-SIDE PRODUCT COMPARISON & VERDICT ENGINE
    // =========================================================================

    public AiCompareResponse compareProducts(AiCompareRequest request) {
        if (request.getProduct1Id() == null || request.getProduct2Id() == null) {
            throw new InvalidProductException("Both product1Id and product2Id are required for comparison");
        }

        Product p1 = productRepository.findById(request.getProduct1Id())
                .orElseThrow(() -> new InvalidProductException("Product 1 not found with ID: " + request.getProduct1Id()));
        Product p2 = productRepository.findById(request.getProduct2Id())
                .orElseThrow(() -> new InvalidProductException("Product 2 not found with ID: " + request.getProduct2Id()));

        String preference = request.getPreference() != null && !request.getPreference().isBlank()
                ? request.getPreference()
                : "Overall Performance & Value for Money";

        try {
            AiVerdictDto verdict = callGroqForComparison(p1, p2, preference);
            return AiCompareResponse.builder()
                    .product1(p1)
                    .product2(p2)
                    .verdict(verdict)
                    .build();
        } catch (Exception e) {
            System.err.println("Groq AI Compare call failed, using smart fallback: " + e.getMessage());
            AiVerdictDto fallbackVerdict = fallbackRuleBasedComparison(p1, p2, preference);
            return AiCompareResponse.builder()
                    .product1(p1)
                    .product2(p2)
                    .verdict(fallbackVerdict)
                    .build();
        }
    }

    private AiVerdictDto callGroqForComparison(Product p1, Product p2, String preference) throws Exception {
        String systemPrompt = "You are a Senior Hardware Benchmark & Buying Advisor for ShopVerse. " +
                "You are comparing two electronic products side by side. " +
                "Analyze their technical specifications (RAM, CPU, Storage, Price, and Brand value) " +
                "and give an objective, expert verdict. " +
                "Return strictly valid JSON matching this schema: " +
                "{\"winnerTitle\": \"Clear winner name or e.g. Dell XPS 15 for Coding\", " +
                "\"winnerProductId\": 1, " +
                "\"summary\": \"Direct 2-sentence expert verdict comparing both machines\", " +
                "\"product1Pros\": [\"pro 1\", \"pro 2\", \"pro 3\"], " +
                "\"product2Pros\": [\"pro 1\", \"pro 2\", \"pro 3\"], " +
                "\"product1BestFor\": \"Target audience for Product 1 (e.g. Students, Portability, Office)\", " +
                "\"product2BestFor\": \"Target audience for Product 2 (e.g. Gamers, Heavy Compilers, 3D Rendering)\"}";

        String userPrompt = String.format(
                "USER PREFERENCE / USE CASE: %s\n\n" +
                "PRODUCT 1 (ID: %d):\n" +
                "- Name: %s\n- Brand: %s\n- Category: %s\n- Price: ₹%.0f\n- RAM: %s\n- Processor: %s\n- Storage: %s\n- Description: %s\n\n" +
                "PRODUCT 2 (ID: %d):\n" +
                "- Name: %s\n- Brand: %s\n- Category: %s\n- Price: ₹%.0f\n- RAM: %s\n- Processor: %s\n- Storage: %s\n- Description: %s\n\n" +
                "Please deliver an in-depth, decisive comparison verdict in strictly valid JSON format.",
                preference,
                p1.getId(), p1.getName(), p1.getBrand(), p1.getCategory(), p1.getPrice(), p1.getRam(), p1.getProcessor(), p1.getStorage(), p1.getDescription(),
                p2.getId(), p2.getName(), p2.getBrand(), p2.getCategory(), p2.getPrice(), p2.getRam(), p2.getProcessor(), p2.getStorage(), p2.getDescription()
        );

        Map<String, Object> messageSystem = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> messageUser = Map.of("role", "user", "content", userPrompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", groqModel);
        requestBodyMap.put("messages", List.of(messageSystem, messageUser));
        requestBodyMap.put("temperature", 0.2);
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String requestJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(httpResponse.body());
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String aiContent = choices.get(0).path("message").path("content").asText();
                JsonNode json = objectMapper.readTree(aiContent);

                List<String> p1Pros = new ArrayList<>();
                if (json.path("product1Pros").isArray()) {
                    json.path("product1Pros").forEach(n -> p1Pros.add(n.asText()));
                }

                List<String> p2Pros = new ArrayList<>();
                if (json.path("product2Pros").isArray()) {
                    json.path("product2Pros").forEach(n -> p2Pros.add(n.asText()));
                }

                int winnerId = json.path("winnerProductId").asInt(p1.getId());

                return AiVerdictDto.builder()
                        .winnerTitle(json.path("winnerTitle").asText(p1.getName()))
                        .winnerProductId(winnerId)
                        .summary(json.path("summary").asText("Both machines are capable options with distinctive strengths in hardware and pricing."))
                        .product1Pros(p1Pros.isEmpty() ? List.of("Excellent build quality", "Solid spec balance") : p1Pros)
                        .product2Pros(p2Pros.isEmpty() ? List.of("Competitive pricing", "High utility") : p2Pros)
                        .product1BestFor(json.path("product1BestFor").asText("Professionals and power users."))
                        .product2BestFor(json.path("product2BestFor").asText("Budget-conscious and specialized users."))
                        .build();
            }
        }

        throw new RuntimeException("Groq Compare API status: " + httpResponse.statusCode());
    }

    private AiVerdictDto fallbackRuleBasedComparison(Product p1, Product p2, String preference) {
        boolean p1Cheaper = (p1.getPrice() != null && p2.getPrice() != null) && (p1.getPrice() < p2.getPrice());

        List<String> p1Pros = new ArrayList<>();
        List<String> p2Pros = new ArrayList<>();

        if (p1.getRam() != null) p1Pros.add("RAM: " + p1.getRam());
        if (p1.getProcessor() != null) p1Pros.add("Processor: " + p1.getProcessor());
        if (p1Cheaper) p1Pros.add("More budget-friendly price point");

        if (p2.getRam() != null) p2Pros.add("RAM: " + p2.getRam());
        if (p2.getProcessor() != null) p2Pros.add("Processor: " + p2.getProcessor());
        if (!p1Cheaper) p2Pros.add("Cost-effective purchase value");

        Product winner = p1Cheaper ? p1 : p2;

        return AiVerdictDto.builder()
                .winnerTitle(winner.getName() + " (Optimal Value)")
                .winnerProductId(winner.getId())
                .summary(String.format("Comparing %s against %s reveals close performance. %s edges out with better pricing to spec ratio.",
                        p1.getName(), p2.getName(), winner.getName()))
                .product1Pros(p1Pros)
                .product2Pros(p2Pros)
                .product1BestFor("Users seeking balance of hardware and reliability.")
                .product2BestFor("Users needing specific hardware configurations.")
                .build();
    }

    // =========================================================================
    // NATURAL LANGUAGE PROMPT-TO-CART SEARCH ENGINE
    // =========================================================================

    public AiPromptSearchResponse promptSearch(AiPromptSearchRequest request) {
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            return AiPromptSearchResponse.builder()
                    .detectedIntent("Please speak or enter what you are looking for.")
                    .matches(Collections.emptyList())
                    .build();
        }

        List<Product> allProducts = productRepository.findAll();
        if (allProducts.isEmpty()) {
            return AiPromptSearchResponse.builder()
                    .detectedIntent("No inventory currently available in store.")
                    .matches(Collections.emptyList())
                    .build();
        }

        try {
            return callGroqForPromptSearch(request.getPrompt().trim(), allProducts);
        } catch (Exception ex) {
            System.err.println("Groq Prompt Search failed, using smart fallback: " + ex.getMessage());
            return fallbackPromptSearch(request.getPrompt().trim(), allProducts);
        }
    }

    private AiPromptSearchResponse callGroqForPromptSearch(String userPromptText, List<Product> products) throws Exception {
        StringBuilder catalogBuilder = new StringBuilder();
        for (Product p : products) {
            catalogBuilder.append(String.format(
                    "- ID: %d | Name: %s | Price: ₹%.0f | Category: %s | Brand: %s | RAM: %s | Processor: %s | Storage: %s | Description: %s\n",
                    p.getId(), p.getName(), p.getPrice(),
                    p.getCategory() != null ? p.getCategory() : "General",
                    p.getBrand() != null ? p.getBrand() : "Generic",
                    p.getRam() != null ? p.getRam() : "N/A",
                    p.getProcessor() != null ? p.getProcessor() : "N/A",
                    p.getStorage() != null ? p.getStorage() : "N/A",
                    p.getDescription() != null ? p.getDescription().replaceAll("\n", " ") : ""
            ));
        }

        String systemPrompt = "You are ShopVerse's AI Prompt-to-Cart Engine. " +
                "The shopper spoke or typed a natural language sentence describing what tech they want. " +
                "1. Understand their intent (Category, RAM, Processor, Budget, Usage). " +
                "2. Pick 1 to 4 matching products strictly from the store catalog provided. " +
                "Return strictly valid JSON matching this schema: " +
                "{\"detectedIntent\": \"Concise 1-sentence breakdown of what user wants (e.g. 16GB RAM laptop for programming under ₹1,00,000)\", " +
                "\"matches\": [{\"productId\": 1, \"matchScore\": 96, \"matchReason\": \"Directly fits your 16GB RAM requirement and coding needs\", \"highlight\": \"16GB RAM • Core i7\"}]}";

        String userPrompt = "USER SPOKE/TYPED:\n\"" + userPromptText + "\"\n\n" +
                "AVAILABLE STORE INVENTORY:\n" + catalogBuilder + "\n" +
                "Return valid JSON only.";

        Map<String, Object> messageSystem = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> messageUser = Map.of("role", "user", "content", userPrompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", groqModel);
        requestBodyMap.put("messages", List.of(messageSystem, messageUser));
        requestBodyMap.put("temperature", 0.2);
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String requestJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(httpResponse.body());
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String aiContent = choices.get(0).path("message").path("content").asText();
                JsonNode json = objectMapper.readTree(aiContent);

                String detectedIntent = json.path("detectedIntent").asText("Matched against your query: " + userPromptText);

                Map<Integer, Product> productMap = products.stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                List<AiRecommendationItem> matches = new ArrayList<>();
                JsonNode matchNodes = json.path("matches");

                if (matchNodes.isArray()) {
                    for (JsonNode item : matchNodes) {
                        int productId = item.path("productId").asInt();
                        Product p = productMap.get(productId);
                        if (p != null) {
                            matches.add(AiRecommendationItem.builder()
                                    .productId(productId)
                                    .product(p)
                                    .matchScore(item.path("matchScore").asInt(92))
                                    .matchReason(item.path("matchReason").asText("Matches your voice/text criteria."))
                                    .highlight(item.path("highlight").asText("Direct Match"))
                                    .build());
                        }
                    }
                }

                if (!matches.isEmpty()) {
                    return AiPromptSearchResponse.builder()
                            .detectedIntent(detectedIntent)
                            .matches(matches)
                            .build();
                }
            }
        }

        return fallbackPromptSearch(userPromptText, products);
    }

    private AiPromptSearchResponse fallbackPromptSearch(String userPromptText, List<Product> products) {
        String query = userPromptText.toLowerCase();
        List<AiRecommendationItem> items = new ArrayList<>();

        for (Product p : products) {
            int score = 65;
            List<String> highlights = new ArrayList<>();

            if (p.getRam() != null && query.contains(p.getRam().toLowerCase())) {
                score += 20;
                highlights.add(p.getRam() + " RAM");
            }
            if (p.getProcessor() != null && query.contains(p.getProcessor().toLowerCase())) {
                score += 20;
                highlights.add(p.getProcessor());
            }
            if (p.getCategory() != null && query.contains(p.getCategory().toLowerCase())) {
                score += 15;
                highlights.add(p.getCategory());
            }
            if (p.getName() != null && query.contains(p.getName().toLowerCase())) {
                score += 25;
                highlights.add(p.getName());
            }

            if (score > 65) {
                items.add(AiRecommendationItem.builder()
                        .productId(p.getId())
                        .product(p)
                        .matchScore(Math.min(score, 99))
                        .matchReason("Matched keywords in your voice/text prompt.")
                        .highlight(!highlights.isEmpty() ? String.join(" • ", highlights) : "Matched")
                        .build());
            }
        }

        items.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        List<AiRecommendationItem> topMatches = items.stream().limit(4).collect(Collectors.toList());

        if (topMatches.isEmpty() && !products.isEmpty()) {
            topMatches = products.stream().limit(2).map(p -> AiRecommendationItem.builder()
                    .productId(p.getId())
                    .product(p)
                    .matchScore(80)
                    .matchReason("Popular choice in store inventory.")
                    .highlight("Recommended")
                    .build()).collect(Collectors.toList());
        }

        return AiPromptSearchResponse.builder()
                .detectedIntent("Searched for: \"" + userPromptText + "\"")
                .matches(topMatches)
                .build();
    }
}
