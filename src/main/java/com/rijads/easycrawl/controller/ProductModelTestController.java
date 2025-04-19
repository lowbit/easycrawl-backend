package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.utility.ProductTextProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for testing and demonstrating the enhanced model name extraction
 */
@RestController
@RequestMapping("/api/test/model-extraction")
public class ProductModelTestController {

    @Autowired
    private ProductTextProcessor textProcessor;

    /**
     * Test model name extraction on a product title
     */
    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> testModelExtraction(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        if (title == null || title.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Product title is required"
            ));
        }

        Map<String, Object> result = new HashMap<>();

        // Clean the title
        String cleanedTitle = textProcessor.cleanTitle(title);
        result.put("cleanedTitle", cleanedTitle);

        // Extract brand
        String brand = textProcessor.extractBrand(title);
        result.put("brand", brand);

        // Extract model without standardization
        String rawModel = textProcessor.extractModel(title, brand);
        result.put("rawModel", rawModel);

        // Extract standardized model
        String standardizedModel = textProcessor.standardizeModelName(brand, rawModel);
        result.put("standardizedModel", standardizedModel);

        // Additional information that might be useful
        result.put("color", textProcessor.extractColor(title));
        result.put("storage", textProcessor.extractStorageInfo(title));
        result.put("ram", textProcessor.extractRamInfo(title));

        return ResponseEntity.ok(result);
    }

    /**
     * Test similarity between two model names
     */
    @PostMapping("/compare-models")
    public ResponseEntity<Map<String, Object>> testModelComparison(@RequestBody Map<String, String> request) {
        String model1 = request.get("model1");
        String model2 = request.get("model2");

        if (model1 == null || model2 == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Both model1 and model2 are required"
            ));
        }

        // Calculate similarity between the models
        double similarity = calculateModelSimilarity(model1, model2);

        Map<String, Object> result = new HashMap<>();
        result.put("model1", model1);
        result.put("model2", model2);
        result.put("similarity", similarity);
        result.put("similar", similarity >= 0.7);

        return ResponseEntity.ok(result);
    }

    /**
     * Compare two product titles and extract their models
     */
    @PostMapping("/compare-titles")
    public ResponseEntity<Map<String, Object>> testTitleComparison(@RequestBody Map<String, String> request) {
        String title1 = request.get("title1");
        String title2 = request.get("title2");

        if (title1 == null || title2 == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Both title1 and title2 are required"
            ));
        }

        Map<String, Object> result = new HashMap<>();

        // Process first title
        Map<String, Object> title1Results = new HashMap<>();
        title1Results.put("cleanedTitle", textProcessor.cleanTitle(title1));
        String brand1 = textProcessor.extractBrand(title1);
        title1Results.put("brand", brand1);
        String model1 = textProcessor.extractModel(title1, brand1);
        title1Results.put("model", model1);
        title1Results.put("standardizedModel", textProcessor.standardizeModelName(brand1, model1));

        // Process second title
        Map<String, Object> title2Results = new HashMap<>();
        title2Results.put("cleanedTitle", textProcessor.cleanTitle(title2));
        String brand2 = textProcessor.extractBrand(title2);
        title2Results.put("brand", brand2);
        String model2 = textProcessor.extractModel(title2, brand2);
        title2Results.put("model", model2);
        title2Results.put("standardizedModel", textProcessor.standardizeModelName(brand2, model2));

        // Compare
        boolean sameBrand = brand1 != null && brand2 != null && brand1.equalsIgnoreCase(brand2);
        double modelSimilarity = 0.0;
        if (model1 != null && model2 != null) {
            modelSimilarity = calculateModelSimilarity(model1, model2);
        }

        double titleSimilarity = textProcessor.calculateTitleSimilarity(title1, title2);

        result.put("title1", title1Results);
        result.put("title2", title2Results);
        result.put("sameBrand", sameBrand);
        result.put("modelSimilarity", modelSimilarity);
        result.put("titleSimilarity", titleSimilarity);
        result.put("likelySameProduct", sameBrand && modelSimilarity >= 0.7);

        return ResponseEntity.ok(result);
    }

    /**
     * Simplified model similarity calculation
     */
    private double calculateModelSimilarity(String model1, String model2) {
        if (model1 == null || model2 == null) {
            return 0.0;
        }

        // Normalize and tokenize both models
        String[] tokens1 = model1.toLowerCase().split("\\s+");
        String[] tokens2 = model2.toLowerCase().split("\\s+");

        // Count common tokens
        int commonTokens = 0;
        for (String token1 : tokens1) {
            for (String token2 : tokens2) {
                if (token1.equals(token2) || isNumericallyEquivalent(token1, token2)) {
                    commonTokens++;
                    break;
                }
            }
        }

        // Calculate Dice coefficient
        return (2.0 * commonTokens) / (tokens1.length + tokens2.length);
    }

    /**
     * Check if two tokens are numerically equivalent
     */
    private boolean isNumericallyEquivalent(String token1, String token2) {
        // Extract numbers from both tokens
        String num1 = token1.replaceAll("[^0-9]", "");
        String num2 = token2.replaceAll("[^0-9]", "");

        // If both have numbers, compare them
        if (!num1.isEmpty() && !num2.isEmpty()) {
            return num1.equals(num2);
        }

        return false;
    }
}