package com.rijads.easycrawl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.model.UnmappableItem;
import com.rijads.easycrawl.repository.ProductRegistryRepository;
import com.rijads.easycrawl.repository.UnmappableItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to analyze and manage unmappable items
 */
@Service
public class UnmappableItemService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappableItemService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UnmappableItemRepository unmappableItemRepository;

    @Autowired
    private ProductRegistryRepository productRegistryRepository;

    @Autowired
    private ProductMatchingService productMatchingService;

    /**
     * Get summary statistics of unmappable items
     */
    public Map<String, Object> getUnmappableItemStats() {
        Map<String, Object> stats = new HashMap<>();

        // Total count
        long totalCount = unmappableItemRepository.count();
        stats.put("totalCount", totalCount);

        // Counts by reason code
        List<Object[]> reasonStats = unmappableItemRepository.findMostCommonReasons();
        Map<String, Long> reasonCounts = new HashMap<>();
        for (Object[] row : reasonStats) {
            UnmappableItem.ReasonCode code = (UnmappableItem.ReasonCode) row[0];
            Long count = (Long) row[1];
            reasonCounts.put(code.name(), count);
        }
        stats.put("byReason", reasonCounts);

        // Counts by category
        List<Object[]> categoryStats = unmappableItemRepository.findMostProblematicCategories();
        Map<String, Long> categoryCounts = new HashMap<>();
        for (Object[] row : categoryStats) {
            String category = (String) row[0];
            Long count = (Long) row[1];
            categoryCounts.put(category, count);
        }
        stats.put("byCategory", categoryCounts);

        return stats;
    }

    /**
     * Analyze potential missing brands
     */
    public List<Map<String, Object>> analyzePotentialMissingBrands(int minFrequency) {
        // Get missing brand items
        List<UnmappableItem> missingBrandItems = unmappableItemRepository.findByReasonCode(
                UnmappableItem.ReasonCode.MISSING_BRAND);

        if (missingBrandItems.isEmpty()) {
            return Collections.emptyList();
        }

        // Extract potential brand words and count occurrences
        Map<String, Integer> wordFrequency = new HashMap<>();
        Map<String, Set<Integer>> wordToItemIds = new HashMap<>();

        for (UnmappableItem item : missingBrandItems) {
            String title = item.getTitle();
            if (title == null || title.isEmpty()) {
                continue;
            }

            // Get first word (potential brand)
            String[] words = title.trim().split("\\s+");
            if (words.length > 0) {
                String firstWord = words[0].toLowerCase();
                if (firstWord.length() >= 3) {
                    // Count frequency
                    wordFrequency.put(firstWord, wordFrequency.getOrDefault(firstWord, 0) + 1);

                    // Track which items contain this word
                    wordToItemIds.computeIfAbsent(firstWord, k -> new HashSet<>())
                            .add(item.getRawItemId());
                }
            }
        }

        // Filter by minimum frequency
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {
            String word = entry.getKey();
            int frequency = entry.getValue();

            if (frequency >= minFrequency) {
                // Check if this word already exists as a brand in registry
                boolean existsInRegistry = productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                        word, ProductRegistry.RegistryType.BRAND);

                Map<String, Object> brandInfo = new HashMap<>();
                brandInfo.put("word", word);
                brandInfo.put("frequency", frequency);
                brandInfo.put("existsInRegistry", existsInRegistry);
                brandInfo.put("itemCount", wordToItemIds.get(word).size());
                brandInfo.put("sampleItemIds", wordToItemIds.get(word).stream()
                        .limit(5)
                        .collect(Collectors.toList()));

                result.add(brandInfo);
            }
        }

        // Sort by frequency (descending)
        result.sort((a, b) -> {
            Integer freqA = (Integer) a.get("frequency");
            Integer freqB = (Integer) b.get("frequency");
            return freqB.compareTo(freqA);
        });

        return result;
    }

    /**
     * Add potential brands to registry
     */
    @Transactional
    public int addPotentialBrandsToRegistry(List<String> brandWords, String description) {
        if (brandWords == null || brandWords.isEmpty()) {
            return 0;
        }

        int added = 0;

        for (String brand : brandWords) {
            if (brand == null || brand.trim().isEmpty()) {
                continue;
            }

            // Check if brand already exists in registry
            String normalizedBrand = brand.trim();
            if (productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.BRAND)) {
                logger.info("Brand '{}' already exists in registry", normalizedBrand);
                continue;
            }

            // First letter capitalized
            String formattedBrand = normalizedBrand.substring(0, 1).toUpperCase() +
                    normalizedBrand.substring(1).toLowerCase();

            // Create new registry entry
            ProductRegistry entry = new ProductRegistry();
            entry.setRegistryType(ProductRegistry.RegistryType.BRAND);
            entry.setRegistryKey(formattedBrand);
            entry.setDescription(description != null ? description :
                    "Auto-added from unmappable item analysis");
            entry.setEnabled(true);

            try {
                productRegistryRepository.save(entry);
                added++;
                logger.info("Added new brand '{}' to registry", formattedBrand);
            } catch (Exception e) {
                logger.error("Error adding brand '{}' to registry: {}", formattedBrand, e.getMessage());
            }
        }

        return added;
    }

    /**
     * Retry processing unmappable items
     */
    @Transactional
    public Map<String, Object> retryUnmappableItems(int maxAttempts) {
        Map<String, Object> result = new HashMap<>();

        // Delegate to product matching service
        int beforeCount = (int)unmappableItemRepository.count();
        productMatchingService.retryUnmappableItems(maxAttempts);
        int afterCount = (int)unmappableItemRepository.count();

        result.put("beforeCount", beforeCount);
        result.put("afterCount", afterCount);
        result.put("mappedCount", beforeCount - afterCount);

        return result;
    }

    /**
     * Get detailed information about an unmappable item
     */
    public Map<String, Object> getUnmappableItemDetails(Integer rawItemId) {
        Optional<UnmappableItem> itemOpt = unmappableItemRepository.findById(rawItemId);

        if (!itemOpt.isPresent()) {
            return Collections.emptyMap();
        }

        UnmappableItem item = itemOpt.get();
        Map<String, Object> details = new HashMap<>();

        details.put("id", item.getRawItemId());
        details.put("title", item.getTitle());
        details.put("category", item.getCategory());
        details.put("reasonCode", item.getReasonCode().name());
        details.put("reason", item.getReason());
        details.put("attempts", item.getAttempts());
        details.put("firstSeen", item.getFirstSeen());
        details.put("lastAttempt", item.getLastAttempt());

        // Parse extracted data JSON
        try {
            if (item.getExtractedData() != null && !item.getExtractedData().isEmpty()) {
                details.put("extractedData", objectMapper.readValue(item.getExtractedData(), Map.class));
            }
        } catch (Exception e) {
            logger.error("Error parsing extracted data for item {}: {}", rawItemId, e.getMessage());
            details.put("extractedDataError", "Could not parse JSON: " + e.getMessage());
        }

        return details;
    }
}