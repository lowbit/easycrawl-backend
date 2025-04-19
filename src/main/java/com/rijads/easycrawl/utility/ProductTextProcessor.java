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
    private Set<String> brandModifiers = new HashSet<>();

    /**
     * Initialize on startup and refresh periodically
     */
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

        // Initialize brand modifiers based on common words
        brandModifiers = new HashSet<>();

        // Check if these terms exist in commonWords and use them as modifiers
        String[] potentialModifiers = {"lite", "pro", "plus", "ultra", "max", "mini"};
        for (String modifier : potentialModifiers) {
            if (commonWords.contains(modifier.toLowerCase())) {
                brandModifiers.add(modifier.toLowerCase());
            } else {
                // Add them anyway as common modifiers
                brandModifiers.add(modifier.toLowerCase());
            }
        }

        logger.info("Registry refreshed: {} brands, {} common words, {} colors, {} storage patterns",
                knownBrands.size(), commonWords.size(), commonColors.size(), storagePatterns.size());
    }

    /**
     * Cleans a product title by removing common marketing terms and normalizing text
     * Enhanced to better handle marketing terminology
     */
    public String cleanTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }

        String cleaned = title.toLowerCase()
                // Remove content in brackets, parentheses, etc.
                .replaceAll("\\[.*?\\]", " ")
                .replaceAll("\\(.*?\\)", " ")
                .replaceAll("\\{.*?\\}", " ")
                // Remove special characters except alphanumeric, spaces and some punctuation
                .replaceAll("[^\\w\\s\\-\\+]", " ")
                // Remove hashtags
                .replaceAll("#\\w+", "")
                // Collapse multiple spaces
                .replaceAll("\\s+", " ")
                .trim();

        // Remove common marketing words from registry
        for (String word : commonWords) {
            cleaned = cleaned.replaceAll("\\b" + Pattern.quote(word) + "\\b", " ");
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

    /**
     * Helper method to capitalize the first letter of a string
     */
    public String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Extracts the brand from a product title, only using known brands from the registry
     * Returns null if no known brand is found
     */
    public String extractBrand(String title) {
        if (title == null || title.isEmpty() || knownBrands.isEmpty()) {
            return null;
        }

        String cleanedTitle = cleanTitle(title);

        // Find all brand matches in the title
        List<BrandMatch> matches = new ArrayList<>();

        // Check for known brands from registry
        for (String brand : knownBrands) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(brand) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(cleanedTitle);

            while (matcher.find()) {
                int position = matcher.start();
                matches.add(new BrandMatch(brand, position));
            }
        }

        if (matches.isEmpty()) {
            // No brands found
            return null;
        }

        // If only one match, use it
        if (matches.size() == 1) {
            return capitalizeFirstLetter(matches.get(0).brand);
        }

        // Multiple matches - apply contextual filtering

        // First, check for context issues with potential modifier brands
        for (BrandMatch match : matches) {
            if (brandModifiers.contains(match.brand.toLowerCase())) {
                // Check if this is a modifier appearing after other words
                if (match.position > 0) {
                    // There's content before this brand
                    String textBefore = cleanedTitle.substring(0, match.position).trim();
                    if (!textBefore.isEmpty()) {
                        // This is likely a model modifier, not a standalone brand - mark it as such
                        match.isLikelyModifier = true;
                    }
                }
            }
        }

        // Remove likely modifiers if we have other options
        List<BrandMatch> nonModifierMatches = matches.stream()
                .filter(match -> !match.isLikelyModifier)
                .collect(Collectors.toList());

        if (!nonModifierMatches.isEmpty()) {
            // We have matches that aren't modifiers - use those only
            matches = nonModifierMatches;
        }

        // Finally, prefer brands that appear earlier in the title
        matches.sort(Comparator.comparing(match -> match.position));

        return capitalizeFirstLetter(matches.get(0).brand);
    }

    // Helper class for tracking brand matches
    private static class BrandMatch {
        String brand;
        int position;
        boolean isLikelyModifier = false;

        BrandMatch(String brand, int position) {
            this.brand = brand;
            this.position = position;
        }
    }

    /**
     * Extracts the model from a product title, considering the brand if available
     * Significantly enhanced to better identify model names by eliminating marketing terms
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
            // Also remove brand from anywhere in the title - sometimes brands are repeated
            titleWithoutBrand = titleWithoutBrand.replaceAll("(?i)\\b" + Pattern.quote(brand.toLowerCase()) + "\\b", "").trim();
        }

        // 2. Split into words
        String[] words = titleWithoutBrand.split("\\s+");
        if (words.length == 0) {
            return null;
        }

        // 3. Pre-process to detect storage patterns and other non-model information
        Set<String> wordsToExclude = new HashSet<>();

        // Detect and exclude storage patterns
        String storageInfo = extractStorageInfo(titleWithoutBrand);
        if (storageInfo != null) {
            // Look for storage pattern in the words
            String storageDigits = storageInfo.replaceAll("[^0-9]", "");
            for (int i = 0; i < words.length; i++) {
                // Check if this word is related to storage
                if (words[i].contains(storageDigits) || extractStorageInfo(words[i]) != null) {
                    wordsToExclude.add(words[i]);
                }

                // Also exclude standalone "GB" or "TB" words that might follow numbers
                if (i > 0 && (words[i].equalsIgnoreCase("gb") || words[i].equalsIgnoreCase("tb")) &&
                        words[i-1].matches(".*\\d+.*")) {
                    wordsToExclude.add(words[i]);
                }
            }
        }

        // Detect and exclude RAM patterns
        String ramInfo = extractRamInfo(titleWithoutBrand);
        if (ramInfo != null) {
            String ramDigits = ramInfo.replaceAll("[^0-9]", "");
            for (int i = 0; i < words.length; i++) {
                if (words[i].contains(ramDigits) || words[i].matches(".*\\d+\\s*gb\\s*ram.*")) {
                    wordsToExclude.add(words[i]);
                }

                // Also exclude standalone "RAM" or "GB" words related to memory
                if ((i > 0) &&
                        (words[i].equalsIgnoreCase("ram") || words[i].equalsIgnoreCase("gb") ||
                                words[i].equalsIgnoreCase("memory")) &&
                        words[i-1].matches(".*\\d+.*")) {
                    wordsToExclude.add(words[i]);
                    wordsToExclude.add(words[i-1]);
                }
            }
        }

        // Exclude combination patterns like "3+16" or "4+64GB"
        for (int i = 0; i < words.length; i++) {
            if (words[i].matches("\\d+\\+\\d+.*")) {
                wordsToExclude.add(words[i]);
                // Also exclude any "GB" or "TB" that follows
                if (i+1 < words.length && (words[i+1].equalsIgnoreCase("gb") || words[i+1].equalsIgnoreCase("tb"))) {
                    wordsToExclude.add(words[i+1]);
                }
            }
        }

        // Exclude color terms
        for (String color : commonColors) {
            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase(color)) {
                    wordsToExclude.add(words[i]);
                    // Also exclude color-related words
                    if (i > 0 && (words[i-1].equalsIgnoreCase("color") ||
                            words[i-1].equalsIgnoreCase("in") ||
                            words[i-1].equalsIgnoreCase("boja"))) {
                        wordsToExclude.add(words[i-1]);
                    }
                }
            }
        }

        // 4. Look for alphanumeric model number patterns first
        // These are high-confidence model identifiers (e.g., "SM-A515F", "iPhone13,4", "F756GT")
        for (String word : words) {
            if (!wordsToExclude.contains(word)) {
                // Check for patterns that are very likely to be model numbers
                if (word.matches("[a-zA-Z]+[0-9]+[a-zA-Z0-9-]*") ||  // Letters followed by numbers (e.g., "S21")
                        word.matches("[a-zA-Z]+-[0-9a-zA-Z]+") ||        // Letters-numbers with dash (e.g., "SM-A515")
                        word.matches("[0-9]+[a-zA-Z]+[0-9]*")) {         // Numbers followed by letters (e.g., "11Pro")

                    return capitalizeFirstLetter(word);
                }
            }
        }

        // 5. Look for standalone numbers that could be model identifiers
        // (e.g., "14", "22", "11")
        for (String word : words) {
            if (!wordsToExclude.contains(word) && word.matches("\\d+") && word.length() <= 3) {
                // For simple numeric models, try to find if there's a modifier next to it
                int index = -1;
                for (int i = 0; i < words.length; i++) {
                    if (words[i].equals(word)) {
                        index = i;
                        break;
                    }
                }

                if (index != -1) {
                    StringBuilder modelBuilder = new StringBuilder(word);

                    // Check for modifier after the number
                    if (index + 1 < words.length && brandModifiers.contains(words[index + 1].toLowerCase())) {
                        modelBuilder.append(" ").append(words[index + 1]);
                        return capitalizeFirstLetter(modelBuilder.toString());
                    }

                    return capitalizeFirstLetter(word);
                }
            }
        }

        // 6. Build model from remaining words (fallback approach)
        StringBuilder modelBuilder = new StringBuilder();
        int wordsAdded = 0;
        int maxWordsToAdd = 3; // Cap at 3 words for the model - more focused

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
            Pattern colorPattern = Pattern.compile("\\b" + Pattern.quote(color) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = colorPattern.matcher(cleanedTitle);
            if (matcher.find()) {
                return color.toLowerCase();
            }
        }

        return null;
    }

    /**
     * Extracts storage capacity info from a product title
     */
    public String extractStorageInfo(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        // Handle combined RAM+Storage pattern first (e.g., "3+64 GB", "12+1TB")
        Pattern combinedPattern = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)\\s*(?:GB|TB|G|T)?", Pattern.CASE_INSENSITIVE);
        Matcher combinedMatcher = combinedPattern.matcher(title);
        if (combinedMatcher.find()) {
            // Extract just the storage part (second number)
            String storageValue = combinedMatcher.group(2);
            String unit = title.substring(combinedMatcher.end()).trim().toLowerCase().startsWith("tb") ? "TB" : "GB";
            return storageValue + unit;
        }

        // Handle separate storage pattern (e.g., "128GB", "1TB")
        // Try storage patterns from registry
        for (Pattern pattern : storagePatterns) {
            Matcher matcher = pattern.matcher(title);
            if (matcher.find() && matcher.groupCount() > 0) {
                return matcher.group(1).replaceAll("\\s+", "").toUpperCase();
            }
        }

        return null;
    }

    /**
     * Extracts RAM info from a product title
     */
    public String extractRamInfo(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        // Handle combined RAM+Storage pattern (e.g., "3+64 GB", "12+1TB")
        Pattern combinedPattern = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)\\s*(?:GB|TB|G|T)?", Pattern.CASE_INSENSITIVE);
        Matcher combinedMatcher = combinedPattern.matcher(title);
        if (combinedMatcher.find()) {
            // Extract just the RAM part (first number)
            return combinedMatcher.group(1) + "GB";
        }

        // Handle separate RAM pattern (e.g., "8GB RAM")
        Pattern ramPattern = Pattern.compile("(\\d+)\\s*(?:GB|G)\\s*RAM", Pattern.CASE_INSENSITIVE);
        Matcher ramMatcher = ramPattern.matcher(title);
        if (ramMatcher.find()) {
            return ramMatcher.group(1) + "GB";
        }

        return null;
    }

    /**
     * Generates a standardized model name from extracted components
     * to improve consistency across similar products
     */
    public String standardizeModelName(String brand, String extractedModel) {
        if (brand == null || extractedModel == null) {
            return extractedModel;
        }

        // Specific handling for common brands
        if (brand.equalsIgnoreCase("samsung")) {
            // Convert "Galaxy S21" to "S21", "Note 20" to "Note20", etc.
            if (extractedModel.toLowerCase().contains("galaxy")) {
                extractedModel = extractedModel.replaceAll("(?i)galaxy\\s+", "");
            }

            // Remove spaces between model letters and numbers (S 21 → S21)
            extractedModel = extractedModel.replaceAll("([A-Za-z])\\s+(\\d+)", "$1$2");

        } else if (brand.equalsIgnoreCase("apple") || brand.equalsIgnoreCase("iphone")) {
            // Handle iPhone models - standardize to "iPhone 13" format
            if (extractedModel.toLowerCase().contains("iphone")) {
                // Format "iPhone13" to "iPhone 13"
                extractedModel = extractedModel.replaceAll("(?i)iphone(\\d+)", "iPhone $1");

                // Handle Pro/Max variants
                for (String modifier : new String[]{"Pro", "Max", "Plus", "Mini"}) {
                    if (extractedModel.contains(modifier) && !extractedModel.contains(" " + modifier)) {
                        extractedModel = extractedModel.replace(modifier, " " + modifier);
                    }
                }
            }
        }

        // General standardization
        // Ensure consistent spacing around hyphens
        extractedModel = extractedModel.replaceAll("\\s*-\\s*", "-");

        // Make common modifiers consistent - capitalize first letter of each word
        for (String modifier : brandModifiers) {
            if (extractedModel.toLowerCase().contains(modifier)) {
                // Find the modifier with any casing
                Pattern modifierPattern = Pattern.compile(modifier, Pattern.CASE_INSENSITIVE);
                Matcher matcher = modifierPattern.matcher(extractedModel);

                // Replace with properly capitalized version
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, capitalizeFirstLetter(modifier));
                }
                matcher.appendTail(sb);
                extractedModel = sb.toString();
            }
        }

        return extractedModel;
    }
}