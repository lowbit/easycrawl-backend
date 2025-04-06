package com.rijads.easycrawl.utility;

import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.repository.ProductRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ProductTextProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ProductTextProcessor.class);

    @Autowired
    private ProductRegistryRepository registryRepository;

    // Cache the registry data
    private Set<String> knownBrands = new HashSet<>();
    private Set<String> commonWords = new HashSet<>();
    private Set<String> commonColors = new HashSet<>();
    private List<Pattern> storagePatterns = new ArrayList<>();

    // Initialize on startup and refresh periodically
    @Scheduled(fixedRate = 3600000) // Refresh every hour
    public void refreshRegistry() {
        logger.info("Refreshing product registry data");

        // Load brands
        knownBrands = registryRepository
                .findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType.BRAND)
                .stream()
                .map(ProductRegistry::getRegistryKey)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Load common words
        commonWords = registryRepository
                .findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType.COMMON_WORD)
                .stream()
                .map(ProductRegistry::getRegistryKey)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Load colors
        commonColors = registryRepository
                .findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType.COLOR)
                .stream()
                .map(ProductRegistry::getRegistryKey)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Load storage patterns
        storagePatterns = registryRepository
                .findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType.STORAGE_PATTERN)
                .stream()
                .map(registry -> {
                    try {
                        return Pattern.compile(registry.getRegistryKey(), Pattern.CASE_INSENSITIVE);
                    } catch (Exception e) {
                        logger.error("Invalid regex pattern: {}", registry.getRegistryKey(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        logger.info("Registry refreshed: {} brands, {} common words, {} colors, {} storage patterns",
                knownBrands.size(), commonWords.size(), commonColors.size(), storagePatterns.size());
    }

    /**
     * Cleans a product title by removing common marketing terms and normalizing text
     */
    public String cleanTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }

        String cleaned = title.toLowerCase()
                // Remove special characters except alphanumeric, spaces and some punctuation
                .replaceAll("[^\\w\\s\\-\\+]", " ")
                // Remove hashtags (based on your examples)
                .replaceAll("#\\w+", "")
                // Collapse multiple spaces
                .replaceAll("\\s+", " ")
                .trim();

        // Remove common marketing words from registry
        for (String word : commonWords) {
            cleaned = cleaned.replaceAll("\\b" + word + "\\b", "");
        }

        // Collapse multiple spaces again after removing words
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    /**
     * Calculates similarity between two product titles
     * Uses a simple word-based approach for matching
     */
    public double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        String clean1 = cleanTitle(title1);
        String clean2 = cleanTitle(title2);

        Set<String> words1 = new HashSet<>(Arrays.asList(clean1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(clean2.split("\\s+")));

        // Count common words
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        // Union of words
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        // Jaccard similarity
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Extracts the likely brand from a product title
     */
    public String extractBrand(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        String cleanedTitle = cleanTitle(title);

        // Strategy 1: Check against known brands list from registry
        for (String brand : knownBrands) {
            // Look for the brand as a whole word
            Pattern pattern = Pattern.compile("\\b" + brand + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(cleanedTitle);
            if (matcher.find()) {
                return capitalizeFirstLetter(brand);
            }
        }

        // Strategy 2: Check for brand patterns
        String[] patterns = {
                "by\\s+([A-Za-z0-9][A-Za-z0-9\\s&-]{2,25}?)\\s", // "by Samsung"
                "from\\s+([A-Za-z0-9][A-Za-z0-9\\s&-]{2,25}?)\\s" // "from Apple"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(cleanedTitle);
            if (matcher.find()) {
                return capitalizeFirstLetter(matcher.group(1).trim());
            }
        }

        // Strategy 3: First word heuristic if it's not a common word
        String[] words = cleanedTitle.split("\\s+");
        if (words.length > 0 && !commonWords.contains(words[0].toLowerCase())) {
            return capitalizeFirstLetter(words[0]);
        }

        return null;
    }

    /**
     * Extracts the likely model from a product title
     * Uses a more generalizable approach that works across product categories
     */
    public String extractModel(String title, String brand) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        String cleanedTitle = cleanTitle(title);

        // 1. Remove the brand from the beginning if present
        String titleWithoutBrand = cleanedTitle;
        if (brand != null && !brand.isEmpty()) {
            titleWithoutBrand = cleanedTitle.replaceAll("(?i)^\\b" + Pattern.quote(brand.toLowerCase()) + "\\b\\s*", "").trim();
        }

        // 2. Split into words
        String[] words = titleWithoutBrand.split("\\s+");
        if (words.length == 0) {
            return null;
        }

        // 3. Detect color and storage terms to exclude them
        Set<String> wordsToExclude = new HashSet<>();

        // Identify color terms
        for (String color : commonColors) {
            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase(color)) {
                    wordsToExclude.add(words[i]);
                    // Also exclude the preceding word if it's "color" or similar
                    if (i > 0 && (words[i-1].equalsIgnoreCase("color") ||
                            words[i-1].equalsIgnoreCase("in") ||
                            words[i-1].equalsIgnoreCase("boja"))) {
                        wordsToExclude.add(words[i-1]);
                    }
                }
            }
        }

        // Identify storage terms
        for (int i = 0; i < words.length; i++) {
            for (Pattern pattern : storagePatterns) {
                if (pattern.matcher(words[i]).matches()) {
                    wordsToExclude.add(words[i]);
                }
            }
        }

        // 4. Identify the model portion - typically the first 2-3 words after brand that aren't colors or storage
        StringBuilder modelBuilder = new StringBuilder();
        int wordsAdded = 0;
        int maxWordsToAdd = 3; // Cap at 3 words for the model

        for (int i = 0; i < words.length && wordsAdded < maxWordsToAdd; i++) {
            // Skip words to exclude
            if (wordsToExclude.contains(words[i])) {
                continue;
            }

            // Add word to model
            if (modelBuilder.length() > 0) {
                modelBuilder.append(" ");
            }
            modelBuilder.append(words[i]);
            wordsAdded++;

            // Stop if we've added a word with numbers (likely a model number)
            if (words[i].matches(".*\\d+.*")) {
                break;
            }
        }

        String model = modelBuilder.toString().trim();

        // If we couldn't extract anything, fall back to the first word
        if (model.isEmpty() && words.length > 0) {
            model = words[0];
        }

        return capitalizeFirstLetter(model);
    }

    /**
     * Extracts color from product title if present
     */
    public String extractColor(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        String cleanedTitle = cleanTitle(title);

        // Look for color terms from registry
        for (String color : commonColors) {
            Pattern colorPattern = Pattern.compile("\\b" + color + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = colorPattern.matcher(cleanedTitle);
            if (matcher.find()) {
                return color.toLowerCase();
            }
        }

        return null;
    }

    /**
     * Extracts storage capacity info from a smartphone title
     */
    public String extractStorageInfo(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        // Try storage patterns from registry
        for (Pattern pattern : storagePatterns) {
            Matcher matcher = pattern.matcher(title);
            if (matcher.find() && matcher.groupCount() > 0) {
                return matcher.group(1).replaceAll("\\s+", "").toUpperCase();
            }
        }

        return null;
    }
}
