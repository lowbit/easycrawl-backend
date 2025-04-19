package com.rijads.easycrawl.utility;

import com.rijads.easycrawl.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared utility for product similarity calculations
 * Used by ProductMatchingService, ProductConsistencyService, and other related services
 */
@Component
public class ProductSimilarityUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProductSimilarityUtil.class);

    // Thresholds that can be adjusted as needed
    public static final double SIMILARITY_THRESHOLD = 0.7;
    public static final double MERGE_SIMILARITY_THRESHOLD = 0.8;

    @Autowired
    private ProductTextProcessor textProcessor;

    /**
     * Calculate similarity between a product and extracted attributes
     * Used for matching new products and detecting duplicates
     */
    public double calculateProductSimilarity(Product product, String brand, String model, String cleanedTitle) {
        double score = 0;
        double totalWeight = 0;

        // Brand similarity (high weight)
        if (brand != null && product.getBrand() != null) {
            double brandWeight = 0.3;
            double brandSimilarity = brand.equalsIgnoreCase(product.getBrand()) ? 1.0 : 0.0;
            score += brandSimilarity * brandWeight;
            totalWeight += brandWeight;
        }

        // Model similarity with context awareness (highest weight)
        if (model != null && product.getModel() != null) {
            double modelWeight = 0.5;

            // First check exact match
            if (model.equalsIgnoreCase(product.getModel())) {
                score += 1.0 * modelWeight;
            } else {
                // For non-exact matches, analyze token similarity with context
                double modelSimilarity = calculateContextAwareModelSimilarity(model, product.getModel());
                score += modelSimilarity * modelWeight;
            }

            totalWeight += modelWeight;
        }

        // Title similarity (lower weight, fallback)
        if (cleanedTitle != null && product.getName() != null) {
            double titleWeight = 0.2;
            double titleSimilarity = textProcessor.calculateTitleSimilarity(cleanedTitle, product.getName());
            score += titleSimilarity * titleWeight;
            totalWeight += titleWeight;
        }

        // Normalize score
        return totalWeight > 0 ? score / totalWeight : 0;
    }

    /**
     * Calculate similarity between two products
     * Used for product merging operations
     */
    public double calculateProductToProductSimilarity(Product product1, Product product2) {
        double score = 0;
        double totalWeight = 0;

        // Brand similarity
        if (product1.getBrand() != null && product2.getBrand() != null) {
            double brandWeight = 0.3;
            double brandSimilarity = product1.getBrand().equalsIgnoreCase(product2.getBrand()) ? 1.0 : 0.0;
            score += brandSimilarity * brandWeight;
            totalWeight += brandWeight;
        }

        // Model similarity
        if (product1.getModel() != null && product2.getModel() != null) {
            double modelWeight = 0.5;

            // Check for exact match
            if (product1.getModel().equalsIgnoreCase(product2.getModel())) {
                score += 1.0 * modelWeight;
            } else {
                // Use context-aware similarity
                double modelSimilarity = calculateContextAwareModelSimilarity(
                        product1.getModel(), product2.getModel());
                score += modelSimilarity * modelWeight;
            }

            totalWeight += modelWeight;
        }

        // Name similarity
        if (product1.getName() != null && product2.getName() != null) {
            double nameWeight = 0.2;
            double nameSimilarity = textProcessor.calculateTitleSimilarity(
                    product1.getName(), product2.getName());
            score += nameSimilarity * nameWeight;
            totalWeight += nameWeight;
        }

        // Normalize score
        return totalWeight > 0 ? score / totalWeight : 0;
    }

    /**
     * Calculate similarity between model strings with context awareness
     * Handles differences in model variants
     */
    public double calculateContextAwareModelSimilarity(String model1, String model2) {
        // Normalize and tokenize both models
        String[] tokens1 = model1.toLowerCase().split("\\s+");
        String[] tokens2 = model2.toLowerCase().split("\\s+");

        // Common tokens that should match to consider models similar
        Set<String> commonTokens = new HashSet<>();

        // Tokens that exist in only one model but not the other
        Set<String> uniqueTokens1 = new HashSet<>();
        Set<String> uniqueTokens2 = new HashSet<>();

        // First pass - collect common and unique tokens
        for (String token1 : tokens1) {
            boolean found = false;
            for (String token2 : tokens2) {
                if (token1.equals(token2) || isNumericallyEquivalent(token1, token2)) {
                    commonTokens.add(token1);
                    found = true;
                    break;
                }
            }

            if (!found) {
                uniqueTokens1.add(token1);
            }
        }

        // Second pass - collect unique tokens from model2
        for (String token2 : tokens2) {
            boolean found = false;
            for (String token1 : tokens1) {
                if (token2.equals(token1) || isNumericallyEquivalent(token2, token1)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                uniqueTokens2.add(token2);
            }
        }

        // Get total token count for normalization
        int totalTokens = tokens1.length + tokens2.length;

        // If either model has unique tokens, reduce similarity
        // This is a simple approach that works well for most cases
        // The more unique tokens, the less similar the models are
        int uniqueTokenCount = uniqueTokens1.size() + uniqueTokens2.size();

        // Calculate similarity based on common tokens
        double commonTokenScore = totalTokens > 0 ? (2.0 * commonTokens.size()) / totalTokens : 0;

        // Apply a penalty based on the number of unique tokens
        // More unique tokens = higher penalty
        if (uniqueTokenCount > 0) {
            double penaltyFactor = Math.min(0.7, 1.0 - (0.2 * uniqueTokenCount));
            return commonTokenScore * penaltyFactor;
        }

        return commonTokenScore;
    }

    /**
     * Check if two tokens are numerically equivalent
     * e.g. "14" and "14" are equivalent, "14" and "15" are not
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

    /**
     * Extract key search term from title for faster matching
     * Used for initial filtering before detailed similarity calculation
     */
    public String getKeySearchTerm(String cleanedTitle) {
        if (cleanedTitle == null || cleanedTitle.isEmpty()) {
            return null;
        }

        // Split into words
        String[] words = cleanedTitle.split("\\s+");

        // Find the first word that isn't just numbers and is at least 3 chars
        for (String word : words) {
            // Remove any numbers from the word
            String alphaOnly = word.replaceAll("\\d", "");
            if (alphaOnly.length() >= 3) {
                return alphaOnly;
            }
        }

        return null;
    }
}