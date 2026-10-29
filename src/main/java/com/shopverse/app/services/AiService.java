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
    private HttpClient httpClient;

    public AiService(ProductRepository productRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    private synchronized HttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return this.httpClient;
    }

    public AiRecommendResponse getRecommendations(AiRecommendRequest request) {
        List<Product> allProducts = productRepository.findAll();

        if (allProducts.isEmpty()) {
            return AiRecommendResponse.builder()
                    .summary("No products are currently available in the ShopVerse catalog. Please add products from the admin panel first.")
                    .recommendations(Collections.emptyList())
                    .build();
        }

        // Try getting intelligent recommendations from Groq AI if configured
        if (isGroqConfigured()) {
            try {
                return callGroqForRecommendations(request, allProducts);
            } catch (Exception ex) {
                System.err.println("Groq AI Call failed, using smart fallback: " + ex.getMessage());
            }
        }
        return fallbackRuleBasedRecommendation(request, allProducts);
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
                "You must recommend products based SOLELY on the products available in the store inventory provided below. " +
                "CRITICAL FILTERING CONSTRAINTS: " +
                "1. STRICT CATEGORY: If the user specified a Category (e.g. 'Smartphones'), ONLY recommend that category. NEVER recommend Laptops or Audio for Smartphones. " +
                "2. STRICT BUDGET: If user specified Maximum Budget, ONLY recommend products where Price <= Maximum Budget. NEVER recommend products exceeding budget. " +
                "3. STRICT HARDWARE: Match requested RAM and Processor. " +
                "4. ZERO MATCHES: If NO product in the inventory strictly satisfies ALL constraints, return an empty array: {\"summary\": \"No matching products in store inventory for these specifications.\", \"recommendations\": []}. " +
                "5. NEVER invent products or IDs not in the inventory. " +
                "Return strictly valid JSON matching this schema: " +
                "{\"summary\": \"1-2 sentence overall summary of your recommendation\", " +
                "\"recommendations\": [{\"productId\": 1, \"matchScore\": 95, \"matchReason\": \"Detailed explanation\", \"highlight\": \"Key highlight badge\"}]}";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("User's Desired Specifications:\n");
        if (request.getCategory() != null && !request.getCategory().isBlank() && !request.getCategory().equalsIgnoreCase("All")) {
            userPrompt.append("- Category: ").append(request.getCategory()).append("\n");
        }
        if (request.getRam() != null && !request.getRam().isBlank() && !request.getRam().equalsIgnoreCase("Any")) {
            userPrompt.append("- Required RAM: ").append(request.getRam()).append("\n");
        }
        if (request.getProcessor() != null && !request.getProcessor().isBlank() && !request.getProcessor().equalsIgnoreCase("Any")) {
            userPrompt.append("- Required Processor: ").append(request.getProcessor()).append("\n");
        }
        if (request.getStorage() != null && !request.getStorage().isBlank() && !request.getStorage().equalsIgnoreCase("Any")) {
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
        userPrompt.append("\nPlease select up to 4 strictly matching products from the inventory. If none strictly match, return empty recommendations array. Return strictly valid JSON.");

        // Construct request body for Groq (OpenAI-compatible format)
        Map<String, Object> messageSystem = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> messageUser = Map.of("role", "user", "content", userPrompt.toString());

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", groqModel);
        requestBodyMap.put("messages", List.of(messageSystem, messageUser));
        requestBodyMap.put("temperature", 0.1);
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String requestJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(httpResponse.body());
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String aiContent = choices.get(0).path("message").path("content").asText();
                return parseAiJsonResponse(aiContent, products, request);
            }
        }

        throw new RuntimeException("Groq API returned status: " + httpResponse.statusCode() + " body: " + httpResponse.body());
    }

    private AiRecommendResponse parseAiJsonResponse(String aiContent, List<Product> allProducts, AiRecommendRequest request) {
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
                        // Enforce strict constraints on LLM output
                        if (!productMatchesHardwareCriteria(p, request)) {
                            continue;
                        }
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

            if (recommendations.isEmpty()) {
                summary = "No products in our current store inventory strictly match these specifications.";
            }

            return AiRecommendResponse.builder()
                    .summary(summary)
                    .recommendations(recommendations)
                    .build();
        } catch (Exception e) {
            System.err.println("Error parsing AI JSON response: " + e.getMessage());
        }

        return fallbackRuleBasedRecommendation(request, allProducts);
    }

    private AiRecommendResponse fallbackRuleBasedRecommendation(AiRecommendRequest req, List<Product> products) {
        List<AiRecommendationItem> items = new ArrayList<>();

        for (Product p : products) {
            // STRICT FILTERING: Product MUST meet hard criteria (Category & Budget)
            if (!productMatchesHardwareCriteria(p, req)) {
                continue;
            }

            int score = 75;
            List<String> highlights = new ArrayList<>();
            List<String> reasons = new ArrayList<>();

            if (req.getCategory() != null && !req.getCategory().isBlank() && !req.getCategory().equalsIgnoreCase("All")) {
                score += 5;
                highlights.add(p.getCategory() != null ? p.getCategory() : req.getCategory());
                reasons.add("Matches " + req.getCategory() + " category");
            }

            if (req.getRam() != null && !req.getRam().isBlank() && !req.getRam().equalsIgnoreCase("Any")) {
                score += 10;
                highlights.add("RAM: " + (p.getRam() != null ? p.getRam() : req.getRam()));
                reasons.add("Equipped with " + (p.getRam() != null ? p.getRam() : req.getRam()));
            }

            if (req.getProcessor() != null && !req.getProcessor().isBlank() && !req.getProcessor().equalsIgnoreCase("Any")) {
                score += 10;
                highlights.add("CPU: " + (p.getProcessor() != null ? p.getProcessor() : req.getProcessor()));
                reasons.add("Powered by " + (p.getProcessor() != null ? p.getProcessor() : req.getProcessor()));
            }

            if (req.getMaxBudget() != null && req.getMaxBudget() > 0 && p.getPrice() != null) {
                highlights.add("Within Budget (₹" + String.format("%,.0f", p.getPrice()) + ")");
                reasons.add("Fits within your ₹" + String.format("%,.0f", req.getMaxBudget()) + " budget");
            }

            String reason = reasons.isEmpty()
                    ? "Matches your selected hardware criteria."
                    : String.join(". ", reasons) + ".";

            items.add(AiRecommendationItem.builder()
                    .productId(p.getId())
                    .product(p)
                    .matchScore(Math.min(score, 98))
                    .matchReason(reason)
                    .highlight(!highlights.isEmpty() ? highlights.get(0) : "Recommended Spec")
                    .build());
        }

        items.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        List<AiRecommendationItem> topItems = items.stream().limit(4).collect(Collectors.toList());

        String summary;
        if (topItems.isEmpty()) {
            summary = "No products currently available in our store inventory strictly match your selected specifications.";
        } else {
            summary = "Found " + topItems.size() + " genuine in-store product" + (topItems.size() > 1 ? "s" : "") + " matching your exact hardware and budget criteria.";
        }

        return AiRecommendResponse.builder()
                .summary(summary)
                .recommendations(topItems)
                .build();
    }

    private boolean productMatchesHardwareCriteria(Product p, AiRecommendRequest req) {
        if (p == null) return false;

        // 1. Strict Category
        if (req.getCategory() != null && !req.getCategory().isBlank() && !req.getCategory().equalsIgnoreCase("All")) {
            String cat = req.getCategory().toLowerCase();
            String pCat = p.getCategory() != null ? p.getCategory().toLowerCase() : "";
            String pName = p.getName() != null ? p.getName().toLowerCase() : "";

            if (cat.contains("laptop") && !pCat.contains("laptop") && !pName.contains("laptop") && !pName.contains("macbook")) {
                return false;
            }
            if ((cat.contains("phone") || cat.contains("mobile") || cat.contains("smartphone")) &&
                    !pCat.contains("phone") && !pCat.contains("mobile") && !pCat.contains("smartphone") && !pName.contains("phone") && !pName.contains("iphone")) {
                return false;
            }
            if (cat.contains("audio") && !pCat.contains("audio") && !pCat.contains("headphone") && !pName.contains("headphone") && !pName.contains("earbud")) {
                return false;
            }
            if (cat.contains("tablet") && !pCat.contains("tablet") && !pName.contains("tablet") && !pName.contains("ipad")) {
                return false;
            }
            if (cat.contains("gaming") && !pCat.contains("gaming") && !pName.contains("gaming")) {
                return false;
            }
        }

        // 2. Strict Budget Cap
        if (req.getMaxBudget() != null && req.getMaxBudget() > 0) {
            if (p.getPrice() == null || p.getPrice() > req.getMaxBudget()) {
                return false;
            }
        }

        // 3. Strict RAM (if specified and not "Any")
        if (req.getRam() != null && !req.getRam().isBlank() && !req.getRam().equalsIgnoreCase("Any")) {
            Integer ramNum = extractRam(req.getRam());
            if (ramNum != null) {
                String pRam = p.getRam() != null ? p.getRam().toLowerCase() : "";
                String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                String pName = p.getName() != null ? p.getName().toLowerCase() : "";
                boolean hasRam = pRam.contains(String.valueOf(ramNum)) || pDesc.contains(ramNum + "gb") || pDesc.contains(ramNum + " gb") || pName.contains(ramNum + "gb");
                if (!hasRam) {
                    return false;
                }
            }
        }

        // 4. Strict Processor (if specified and not "Any")
        if (req.getProcessor() != null && !req.getProcessor().isBlank() && !req.getProcessor().equalsIgnoreCase("Any")) {
            String proc = req.getProcessor().toLowerCase();
            String pProc = p.getProcessor() != null ? p.getProcessor().toLowerCase() : "";
            String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
            String pName = p.getName() != null ? p.getName().toLowerCase() : "";

            String targetCpu = extractProcessor(proc);
            if (targetCpu != null) {
                boolean hasCpu = pProc.contains(targetCpu) || pDesc.contains(targetCpu) || pName.contains(targetCpu);
                if (!hasCpu) {
                    return false;
                }
            }
        }

        return true;
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

        if (isGroqConfigured()) {
            try {
                AiVerdictDto verdict = callGroqForComparison(p1, p2, preference);
                return AiCompareResponse.builder()
                        .product1(p1)
                        .product2(p2)
                        .verdict(verdict)
                        .build();
            } catch (Exception e) {
                System.err.println("Groq AI Compare call failed, using smart fallback: " + e.getMessage());
            }
        }
        AiVerdictDto fallbackVerdict = fallbackRuleBasedComparison(p1, p2, preference);
        return AiCompareResponse.builder()
                .product1(p1)
                .product2(p2)
                .verdict(fallbackVerdict)
                .build();
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

        HttpResponse<String> httpResponse = getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

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
                    .detectedIntent("Please enter or speak what you are looking for.")
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

        if (isGroqConfigured()) {
            try {
                return callGroqForPromptSearch(request.getPrompt().trim(), allProducts);
            } catch (Exception ex) {
                System.err.println("Groq Prompt Search failed, using smart fallback: " + ex.getMessage());
            }
        }
        return fallbackPromptSearch(request.getPrompt().trim(), allProducts);
    }

    private AiPromptSearchResponse callGroqForPromptSearch(String userPromptText, List<Product> products) throws Exception {
        String query = userPromptText.toLowerCase().trim();
        Double extractedBudget = extractBudget(query);
        Integer extractedRam = extractRam(query);
        String extractedCategory = extractCategory(query);
        String extractedBrand = extractBrand(query);

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
                "The shopper typed a natural language query describing what tech they want. " +
                "CRITICAL FILTERING CONSTRAINTS: " +
                "1. STRICT CATEGORY: If the user asks for 'phones', ONLY select phones/smartphones. NEVER select laptops, audio, or other categories. " +
                "2. STRICT BUDGET: If user specifies a budget (e.g. 'under 20k' or 'under 20000'), ONLY select products where Price <= budget. NEVER return products that exceed the budget. " +
                "3. STRICT SPECS: If user asks for specific RAM, brand, or processor, only select matching items. " +
                "4. ZERO MATCHES: If NO product in the inventory strictly satisfies ALL constraints, return an empty matches list: {\"detectedIntent\": \"...\", \"matches\": []}. " +
                "5. NEVER invent or recommend substitute products that violate budget or category. " +
                "Return strictly valid JSON matching this schema: " +
                "{\"detectedIntent\": \"Concise breakdown of user request\", " +
                "\"matches\": [{\"productId\": 1, \"matchScore\": 96, \"matchReason\": \"Fits budget and specifications\", \"highlight\": \"Under ₹20k\"}]}";

        String userPrompt = "USER QUERY:\n\"" + userPromptText + "\"\n\n" +
                "STORE INVENTORY:\n" + catalogBuilder + "\n" +
                "Return strictly valid JSON matching the schema.";

        Map<String, Object> messageSystem = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> messageUser = Map.of("role", "user", "content", userPrompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", groqModel);
        requestBodyMap.put("messages", List.of(messageSystem, messageUser));
        requestBodyMap.put("temperature", 0.1);
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String requestJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(httpResponse.body());
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String aiContent = choices.get(0).path("message").path("content").asText();
                JsonNode json = objectMapper.readTree(aiContent);

                String detectedIntent = json.path("detectedIntent").asText("Query: " + userPromptText);

                Map<Integer, Product> productMap = products.stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                List<AiRecommendationItem> matches = new ArrayList<>();
                JsonNode matchNodes = json.path("matches");

                if (matchNodes.isArray()) {
                    for (JsonNode item : matchNodes) {
                        int productId = item.path("productId").asInt();
                        Product p = productMap.get(productId);
                        if (p != null) {
                            // Enforce strict constraints on LLM output to guarantee correctness
                            if (!productMatchesStrictConstraints(p, extractedCategory, extractedBudget, extractedRam, extractedBrand)) {
                                continue;
                            }
                            matches.add(AiRecommendationItem.builder()
                                    .productId(productId)
                                    .product(p)
                                    .matchScore(item.path("matchScore").asInt(92))
                                    .matchReason(item.path("matchReason").asText("Matches your criteria."))
                                    .highlight(item.path("highlight").asText("Direct Match"))
                                    .build());
                        }
                    }
                }

                // If no items passed strict constraints, return zero matches (do not fallback to dummy products)
                return AiPromptSearchResponse.builder()
                        .detectedIntent(detectedIntent)
                        .matches(matches)
                        .build();
            }
        }

        return fallbackPromptSearch(userPromptText, products);
    }

    private AiPromptSearchResponse fallbackPromptSearch(String userPromptText, List<Product> products) {
        String query = userPromptText.toLowerCase().trim();

        // 1. Extract Budget
        Double extractedBudget = extractBudget(query);

        // 2. Extract RAM requirement (e.g. 8, 16, 32, 64)
        Integer extractedRam = extractRam(query);

        // 3. Extract Category
        String extractedCategory = extractCategory(query);

        // 4. Extract Brand
        String extractedBrand = extractBrand(query);

        // 5. Extract Processor keyword
        String extractedProcessor = extractProcessor(query);

        // Build human-friendly detected intent
        List<String> intentParts = new ArrayList<>();
        if (extractedBrand != null) intentParts.add(capitalize(extractedBrand));
        if (extractedCategory != null) intentParts.add(capitalize(extractedCategory) + "s");
        if (extractedRam != null) intentParts.add(extractedRam + "GB RAM");
        if (extractedProcessor != null) intentParts.add(extractedProcessor.toUpperCase());
        if (extractedBudget != null) intentParts.add("under ₹" + String.format("%,.0f", extractedBudget));

        String detectedIntent;
        if (!intentParts.isEmpty()) {
            detectedIntent = "Looking for " + String.join(", ", intentParts);
        } else {
            detectedIntent = "Searching store catalog for: \"" + userPromptText + "\"";
        }

        List<AiRecommendationItem> items = new ArrayList<>();

        for (Product p : products) {
            // STRICT FILTERING: If any extracted constraint is violated, skip product immediately!
            if (!productMatchesStrictConstraints(p, extractedCategory, extractedBudget, extractedRam, extractedBrand)) {
                continue;
            }

            int score = 70;
            List<String> highlights = new ArrayList<>();
            List<String> reasons = new ArrayList<>();

            String pCat = p.getCategory() != null ? p.getCategory().toLowerCase() : "";
            String pName = p.getName() != null ? p.getName().toLowerCase() : "";
            String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
            String pRam = p.getRam() != null ? p.getRam().toLowerCase() : "";
            String pProc = p.getProcessor() != null ? p.getProcessor().toLowerCase() : "";
            String pBrand = p.getBrand() != null ? p.getBrand().toLowerCase() : "";

            if (extractedCategory != null) {
                score += 10;
                highlights.add(p.getCategory() != null ? p.getCategory() : capitalize(extractedCategory));
                reasons.add("Fits your " + extractedCategory + " category");
            }

            if (extractedRam != null) {
                score += 10;
                highlights.add(p.getRam() != null ? p.getRam() : extractedRam + "GB RAM");
                reasons.add("Has requested " + extractedRam + "GB RAM");
            }

            if (extractedProcessor != null) {
                if (pProc.contains(extractedProcessor) || pDesc.contains(extractedProcessor) || pName.contains(extractedProcessor)) {
                    score += 10;
                    highlights.add(p.getProcessor() != null ? p.getProcessor() : extractedProcessor);
                    reasons.add("Equipped with " + (p.getProcessor() != null ? p.getProcessor() : extractedProcessor));
                }
            }

            if (extractedBrand != null) {
                score += 10;
                highlights.add(p.getBrand() != null ? p.getBrand() : capitalize(extractedBrand));
                reasons.add("Genuine " + capitalize(extractedBrand) + " product");
            }

            if (extractedBudget != null && p.getPrice() != null) {
                highlights.add("Within Budget (₹" + String.format("%,.0f", p.getPrice()) + ")");
                reasons.add("Priced at ₹" + String.format("%,.0f", p.getPrice()) + " which fits under your ₹" + String.format("%,.0f", extractedBudget) + " budget");
            }

            // General keyword matching
            String[] tokens = query.split("[\\s,;]+");
            int tokenMatches = 0;
            for (String t : tokens) {
                if (t.length() <= 2) continue;
                if (isStopWord(t)) continue;
                if (pName.contains(t) || pDesc.contains(t)) {
                    tokenMatches++;
                }
            }
            if (tokenMatches > 0) {
                score += Math.min(tokenMatches * 5, 10);
            }

            String matchReason = reasons.isEmpty()
                    ? "Matches your voice/prompt query based on store specifications."
                    : String.join(". ", reasons) + ".";

            items.add(AiRecommendationItem.builder()
                    .productId(p.getId())
                    .product(p)
                    .matchScore(Math.min(score, 98))
                    .matchReason(matchReason)
                    .highlight(!highlights.isEmpty() ? String.join(" • ", highlights) : "Store Match")
                    .build());
        }

        items.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        List<AiRecommendationItem> topMatches = items.stream().limit(6).collect(Collectors.toList());

        // CRITICAL: DO NOT inject arbitrary fallback products if no products matched!
        return AiPromptSearchResponse.builder()
                .detectedIntent(detectedIntent)
                .matches(topMatches)
                .build();
    }

    private boolean productMatchesStrictConstraints(Product p, String category, Double maxBudget, Integer ram, String brand) {
        if (p == null) return false;

        // 1. Strict Category Constraint
        if (category != null && !matchesCategory(p, category)) {
            return false;
        }

        // 2. Strict Budget Constraint: Price MUST be <= budget
        if (maxBudget != null) {
            if (p.getPrice() == null || p.getPrice() > maxBudget) {
                return false;
            }
        }

        // 3. Strict RAM Constraint
        if (ram != null) {
            String pRam = p.getRam() != null ? p.getRam().toLowerCase() : "";
            String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
            String pName = p.getName() != null ? p.getName().toLowerCase() : "";
            boolean hasRam = pRam.contains(String.valueOf(ram)) || pDesc.contains(ram + "gb") || pDesc.contains(ram + " gb") || pName.contains(ram + "gb");
            if (!hasRam) {
                return false;
            }
        }

        // 4. Strict Brand Constraint
        if (brand != null) {
            String pBrand = p.getBrand() != null ? p.getBrand().toLowerCase() : "";
            String pName = p.getName() != null ? p.getName().toLowerCase() : "";
            if (!pBrand.contains(brand) && !pName.contains(brand)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesCategory(Product p, String cat) {
        if (p == null || cat == null) return true;
        String pCat = p.getCategory() != null ? p.getCategory().toLowerCase() : "";
        String pName = p.getName() != null ? p.getName().toLowerCase() : "";
        String pDesc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";

        if (cat.equals("phone")) {
            return pCat.contains("phone") || pCat.contains("mobile") || pCat.contains("smartphone") ||
                   pName.contains("phone") || pName.contains("iphone") || pName.contains("galaxy") || pDesc.contains("smartphone") || pDesc.contains("5g phone");
        }
        if (cat.equals("laptop")) {
            return pCat.contains("laptop") || pCat.contains("notebook") || pCat.contains("ultrabook") ||
                   pName.contains("laptop") || pName.contains("macbook") || pName.contains("thinkpad") || pDesc.contains("laptop");
        }
        if (cat.equals("audio")) {
            return pCat.contains("audio") || pCat.contains("headphone") || pCat.contains("earphone") ||
                   pName.contains("headphone") || pName.contains("earphone") || pName.contains("earbud") || pName.contains("airpod");
        }
        if (cat.equals("tablet")) {
            return pCat.contains("tablet") || pName.contains("tablet") || pName.contains("ipad");
        }
        if (cat.equals("gaming")) {
            return pCat.contains("gaming") || pName.contains("gaming") || pDesc.contains("gaming");
        }
        return pCat.contains(cat) || pName.contains(cat);
    }

    private boolean isGroqConfigured() {
        return groqApiKey != null && !groqApiKey.isBlank() && !groqApiKey.contains("${") && !groqApiKey.equalsIgnoreCase("none");
    }

    private Double extractBudget(String query) {
        String clean = query.replace(",", "").toLowerCase();

        // 1. Explicit budget indicator (e.g. under 20k, below 20000, less than 20k, within 20k, budget 20k, max 20k)
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(
                "(?:under|below|less than|within|budget of?|max|upto|up to|cheaper than|<=|<)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(k|thousand|lakh|lac)?(?!(?:gb|tb|mb|ram|inch|hz))",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m1 = p1.matcher(clean);
        if (m1.find()) {
            return parseBudgetValue(m1.group(1), m1.group(2));
        }

        // 2. Currency symbol followed by number (₹20000, rs 20k)
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                "(?:rs\\.?|inr|₹)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(k|thousand|lakh|lac)?(?!(?:gb|tb|mb|ram|inch|hz))",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m2 = p2.matcher(clean);
        if (m2.find()) {
            return parseBudgetValue(m2.group(1), m2.group(2));
        }

        // 3. Number with k or lakh suffix (e.g. 20k phone)
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
                "\\b([0-9]+(?:\\.[0-9]+)?)\\s*(k|lakh|lac)\\b(?!(?:gb|tb|mb|ram|inch|hz))",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m3 = p3.matcher(clean);
        if (m3.find()) {
            return parseBudgetValue(m3.group(1), m3.group(2));
        }

        return null;
    }

    private Double parseBudgetValue(String numStr, String unit) {
        try {
            double val = Double.parseDouble(numStr);
            if (unit != null) {
                String u = unit.toLowerCase();
                if (u.startsWith("k") || u.startsWith("thous")) {
                    val *= 1000;
                } else if (u.startsWith("lak") || u.startsWith("lac")) {
                    val *= 100000;
                }
            }
            return val;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer extractRam(String query) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b(4|8|12|16|24|32|64)\\s*(?:gb|gig)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m = p.matcher(query);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractCategory(String query) {
        String q = query.toLowerCase();
        if (q.matches(".*\\b(phones?|smartphones?|mobiles?|iphones?|androids?)\\b.*")) return "phone";
        if (q.matches(".*\\b(laptops?|macbooks?|notebooks?|chromebooks?|ultrabooks?)\\b.*")) return "laptop";
        if (q.matches(".*\\b(headphones?|earphones?|earbuds?|airpods?|headsets?|audios?)\\b.*")) return "audio";
        if (q.matches(".*\\b(tablets?|ipads?)\\b.*")) return "tablet";
        if (q.matches(".*\\b(gamings?|consoles?)\\b.*")) return "gaming";
        return null;
    }

    private String extractBrand(String query) {
        String q = query.toLowerCase();
        String[] brands = {"apple", "asus", "dell", "hp", "lenovo", "samsung", "acer", "msi", "sony", "boat", "oneplus", "google", "xiaomi", "motorola", "realme"};
        for (String b : brands) {
            if (q.contains(b)) return b;
        }
        return null;
    }

    private String extractProcessor(String query) {
        String q = query.toLowerCase();
        if (q.contains("i9") || q.contains("core i9")) return "i9";
        if (q.contains("i7") || q.contains("core i7")) return "i7";
        if (q.contains("i5") || q.contains("core i5")) return "i5";
        if (q.contains("i3") || q.contains("core i3")) return "i3";
        if (q.contains("ryzen 9")) return "ryzen 9";
        if (q.contains("ryzen 7")) return "ryzen 7";
        if (q.contains("ryzen 5")) return "ryzen 5";
        if (q.contains("m1") || q.contains("m2") || q.contains("m3") || q.contains("m4")) return "apple";
        if (q.contains("snapdragon")) return "snapdragon";
        if (q.contains("intel")) return "intel";
        if (q.contains("amd") || q.contains("ryzen")) return "amd";
        return null;
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "i", "want", "need", "for", "a", "an", "the", "with", "show", "me", "find", "get", "buy", "looking", "please", "can", "you", "good", "best"
        );
        return stopWords.contains(word.toLowerCase());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
