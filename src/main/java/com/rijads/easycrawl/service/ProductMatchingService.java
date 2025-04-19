package com.rijads.easycrawl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rijads.easycrawl.model.*;
import com.rijads.easycrawl.repository.*;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(ProductMatchingService.class);
    private static final int MAX_SIMILAR_ITEMS_TO_CHECK = 20; // Limit similar item search
    private static final int MAX_CANDIDATES_TO_CHECK = 30; // Limit candidate products to check
    private static final double SIMILARITY_THRESHOLD = 0.70; // Threshold for product similarity
    private static final double MERGE_SIMILARITY_THRESHOLD = 0.85; // Threshold for merging products

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CrawlerRawRepository crawlerRawRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRegistryRepository productRegistryRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductTextProcessor textProcessor;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UnmappableItemRepository unmappableItemRepository;

    /** Scheduled job to process new raw products */
    @Scheduled(fixedRate = 900000, initialDelay = 15000)
    @Transactional
    public void processNewRawProducts() {
        logger.info("Starting scheduled product processing job");

        List<CrawlerRaw> unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();
        logger.info("Found {} unprocessed raw products", unprocessedItems.size());

        int processed = 0;
        int skipped = 0;
        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                boolean mapped = processRawProduct(rawItem);

                if (mapped) {
                    processed++;
                } else {
                    skipped++;
                }

                // Process in smaller batches to avoid long transactions
                if ((processed + skipped) % 50 == 0) {
                    logger.info(
                            "Processed {} of {} raw products ({} skipped and tracked)",
                            processed,
                            unprocessedItems.size(),
                            skipped);
                }
            } catch (Exception e) {
                logger.error(
                        "Error processing raw product with id {}: {}",
                        rawItem.getId(),
                        e.getMessage(),
                        e);
                // Log the error in the unmappable item table
                trackUnmappableItem(
                        rawItem,
                        UnmappableItem.ReasonCode.OTHER,
                        "Error processing: " + e.getMessage());
            }
        }

        logger.info(
                "Completed processing {} raw products ({} skipped and tracked)",
                processed,
                skipped);
    }

    /**
     * Process a single raw product with improved model name extraction
     */
    @Transactional
    public boolean processRawProduct(CrawlerRaw rawItem) {
        // Skip if already processed
        if (Boolean.TRUE.equals(rawItem.getProcessed())) {
            return true;
        }

        // Extract category from config code (e.g., "domod.ba/smartphones" -> "smartphones")
        String categoryCode = extractCategory(rawItem.getConfigCode());

        // Clean and extract product info
        String cleanedTitle = textProcessor.cleanTitle(rawItem.getTitle());
        String brand = textProcessor.extractBrand(rawItem.getTitle());

        // Fast path: if no brand, check a limited set of similar items and then track as unmappable
        if (brand == null || brand.isEmpty()) {
            logger.info("No brand detected from registry for product: {}", rawItem.getTitle());

            // Quick check for similar mapped products (with timeout/limits)
            Optional<Product> similarMappedProduct =
                    findSimilarMappedProductFast(rawItem, cleanedTitle, categoryCode);

            if (similarMappedProduct.isPresent()) {
                Product matchedProduct = similarMappedProduct.get();
                brand = matchedProduct.getBrand(); // Use the brand from the matched product

                // Extract additional info for the variant
                String model = textProcessor.extractModel(rawItem.getTitle(), brand);
                String color = textProcessor.extractColor(rawItem.getTitle());
                String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());
                String property1 = "";
                if (categoryCode.equalsIgnoreCase("smartphones")) {
                    property1 = textProcessor.extractRamInfo(rawItem.getTitle());
                }

                logger.info(
                        "Found similar mapped product with brand '{}': {}",
                        brand,
                        matchedProduct.getName());

                // Add as variant to the matched product
                addVariantToProduct(matchedProduct, rawItem, color, storageInfo, property1);
                rawItem.setMatchedProductId(matchedProduct.getId());
                rawItem.setProcessed(true);

                // Remove from unmappable items if previously added
                unmappableItemRepository.findById(rawItem.getId())
                        .ifPresent(unmappableItemRepository::delete);

                return true;
            } else {
                // Track the unmappable item with reason but MARK AS PROCESSED
                trackUnmappableItem(
                        rawItem,
                        UnmappableItem.ReasonCode.MISSING_BRAND,
                        "No brand found in registry and no similar products found");

                // Mark as processed even though it couldn't be mapped
                rawItem.setProcessed(true);
                return false;
            }
        }

        // Continue with normal processing if we have a brand
        String extractedModel = textProcessor.extractModel(rawItem.getTitle(), brand);

        // Standardize the model name for better consistency
        String model = textProcessor.standardizeModelName(brand, extractedModel);

        String color = textProcessor.extractColor(rawItem.getTitle());
        String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());
        String property1 = "";
        if (categoryCode.equalsIgnoreCase("smartphones")) {
            property1 = textProcessor.extractRamInfo(rawItem.getTitle());
        }

        // Check if a product with the same brand and model already exists
        List<Product> exactBrandModelMatches = findExactBrandModelMatches(brand, model);

        if (!exactBrandModelMatches.isEmpty()) {
            // Use the first exact match
            Product matchedProduct = exactBrandModelMatches.get(0);
            addVariantToProduct(matchedProduct, rawItem, color, storageInfo, property1);
            rawItem.setMatchedProductId(matchedProduct.getId());
            rawItem.setProcessed(true);

            logger.info(
                    "Found exact brand/model match for '{}': {} {}",
                    rawItem.getTitle(),
                    matchedProduct.getBrand(),
                    matchedProduct.getModel());

            return true;
        }

        // If no exact match, find potential matching products based on similarity
        List<Product> candidates = findCandidateProducts(brand, model, categoryCode);

        Product bestMatch = null;
        double highestSimilarity = 0;

        // Limit the number of candidates we check for performance
        int candidatesToCheck = Math.min(candidates.size(), MAX_CANDIDATES_TO_CHECK);

        // Find best match among candidates
        for (int i = 0; i < candidatesToCheck; i++) {
            Product candidate = candidates.get(i);
            double similarity = calculateProductSimilarity(candidate, brand, model, cleanedTitle);

            if (similarity > SIMILARITY_THRESHOLD && similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = candidate;
            }
        }

        // Either add as variant to existing product or create new product
        if (bestMatch != null) {
            addVariantToProduct(bestMatch, rawItem, color, storageInfo, property1);
            rawItem.setMatchedProductId(bestMatch.getId());
            rawItem.setProcessed(true);

            // If this item was previously unmappable, remove it from the unmappable tracking
            unmappableItemRepository.findById(rawItem.getId())
                    .ifPresent(unmappableItemRepository::delete);
        } else {
            // No good match found - create a new product
            // First double check that we don't already have this brand+model combination
            Product existingProduct = findProductByBrandAndModel(brand, model);

            if (existingProduct != null) {
                // Use the existing product instead of creating a duplicate
                addVariantToProduct(existingProduct, rawItem, color, storageInfo, property1);
                rawItem.setMatchedProductId(existingProduct.getId());
                rawItem.setProcessed(true);

                logger.info(
                        "Found existing product with same brand/model for '{}': {} {}",
                        rawItem.getTitle(),
                        existingProduct.getBrand(),
                        existingProduct.getModel());
            } else {
                // Safe to create new product - truly doesn't exist
                Product newProduct = createNewProduct(rawItem, cleanedTitle, brand, model, categoryCode);
                addVariantToProduct(newProduct, rawItem, color, storageInfo, property1);
                rawItem.setMatchedProductId(newProduct.getId());
                rawItem.setProcessed(true);

                logger.info(
                        "Created new product for '{}' with brand '{}' and model '{}'",
                        rawItem.getTitle(),
                        brand,
                        model);
            }

            // If this item was previously unmappable, remove it from the unmappable tracking
            unmappableItemRepository.findById(rawItem.getId())
                    .ifPresent(unmappableItemRepository::delete);
        }

        return true;
    }

    /**
     * Find products with exact brand and model match
     */
    private List<Product> findExactBrandModelMatches(String brand, String model) {
        if (brand == null || model == null) {
            return Collections.emptyList();
        }

        // First try exact case-insensitive match
        return productRepository.findByBrandIgnoreCaseAndModelIgnoreCase(brand, model);
    }

    /**
     * Find a product by brand and model with more flexible matching
     */
    private Product findProductByBrandAndModel(String brand, String model) {
        if (brand == null || model == null) {
            return null;
        }

        // Try exact match first
        List<Product> exactMatches = findExactBrandModelMatches(brand, model);
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(0);
        }

        // Try a more flexible search
        List<Product> brandMatches = productRepository.findByBrandIgnoreCase(brand);

        // Filter for high model similarity
        for (Product product : brandMatches) {
            if (product.getModel() != null) {
                double modelSimilarity = calculateContextAwareModelSimilarity(model, product.getModel());
                if (modelSimilarity > MERGE_SIMILARITY_THRESHOLD) {
                    return product; // Found a very similar model
                }
            }
        }

        return null; // No sufficiently similar product found
    }

    /**
     * Find similar already mapped products quickly
     */
    private Optional<Product> findSimilarMappedProductFast(
            CrawlerRaw rawItem, String cleanedTitle, String categoryCode) {
        // First check: Look for products with exact matching titles
        String searchTerm = getKeySearchTerm(cleanedTitle);
        if (searchTerm != null && searchTerm.length() >= 3) {
            // Search for products with this key term
            List<Product> exactMatches = productRepository.searchProducts(searchTerm);
            exactMatches =
                    exactMatches.stream()
                            .limit(MAX_SIMILAR_ITEMS_TO_CHECK)
                            .filter(p -> p.getBrand() != null && !p.getBrand().isEmpty())
                            .collect(Collectors.toList());

            if (!exactMatches.isEmpty()) {
                // Simple check for highest title similarity
                Product bestMatch = null;
                double bestSimilarity = 0.5; // Higher threshold for quick match

                for (Product product : exactMatches) {
                    if (product.getName() != null) {
                        double similarity = textProcessor.calculateTitleSimilarity(
                                cleanedTitle, product.getName());
                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity;
                            bestMatch = product;
                        }
                    }
                }

                if (bestMatch != null) {
                    return Optional.of(bestMatch);
                }
            }
        }

        // Second check: Find most recently processed items with the same category
        List<CrawlerRaw> recentlyProcessedItems =
                crawlerRawRepository.findTop20ByProcessedTrueAndConfigCodeContainingOrderByIdDesc(
                        categoryCode);

        if (!recentlyProcessedItems.isEmpty()) {
            // Quick similarity check on these items
            CrawlerRaw mostSimilar = null;
            double highestSimilarity = 0.65; // Higher threshold for quick matches

            for (CrawlerRaw item : recentlyProcessedItems) {
                if (item.getTitle() != null && item.getMatchedProductId() != null) {
                    double similarity =
                            textProcessor.calculateTitleSimilarity(cleanedTitle, item.getTitle());
                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity;
                        mostSimilar = item;
                    }
                }
            }

            if (mostSimilar != null && mostSimilar.getMatchedProductId() != null) {
                // Get the product this item was mapped to
                Optional<Product> product =
                        productRepository.findById(mostSimilar.getMatchedProductId());
                if (product.isPresent()
                        && product.get().getBrand() != null
                        && !product.get().getBrand().isEmpty()) {
                    return product;
                }
            }
        }

        return Optional.empty();
    }

    /** Track an unmappable item in the dedicated table */
    private void trackUnmappableItem(
            CrawlerRaw rawItem, UnmappableItem.ReasonCode reasonCode, String reason) {
        // Check if we already have an entry for this item
        UnmappableItem unmappableItem =
                unmappableItemRepository.findById(rawItem.getId()).orElse(new UnmappableItem());

        // Set basic information
        unmappableItem.setRawItemId(rawItem.getId());
        unmappableItem.setTitle(rawItem.getTitle());
        unmappableItem.setConfigCode(rawItem.getConfigCode());
        unmappableItem.setCategory(extractCategory(rawItem.getConfigCode()));
        unmappableItem.setReasonCode(reasonCode);
        unmappableItem.setReason(reason);

        // Store extracted data as JSON
        try {
            ObjectNode extractedData = objectMapper.createObjectNode();

            String cleanedTitle = textProcessor.cleanTitle(rawItem.getTitle());
            String brand = textProcessor.extractBrand(rawItem.getTitle());
            String model = textProcessor.extractModel(rawItem.getTitle(), brand);
            String color = textProcessor.extractColor(rawItem.getTitle());
            String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());
            String ramInfo = textProcessor.extractRamInfo(rawItem.getTitle());

            extractedData.put("cleanedTitle", cleanedTitle);
            extractedData.put("brand", brand);
            extractedData.put("model", model);
            extractedData.put("color", color);
            extractedData.put("storageInfo", storageInfo);
            extractedData.put("ramInfo", ramInfo);

            unmappableItem.setExtractedData(objectMapper.writeValueAsString(extractedData));
        } catch (Exception e) {
            logger.error(
                    "Error serializing extracted data for unmappable item: {}", e.getMessage());
            unmappableItem.setExtractedData("Error generating extracted data: " + e.getMessage());
        }

        // Save the unmappable item
        unmappableItemRepository.save(unmappableItem);
    }

    /** Find potential matching products based on brand and model */
    private List<Product> findCandidateProducts(String brand, String model, String categoryCode) {
        List<Product> candidates = new ArrayList<>();

        // First try exact brand match
        if (brand != null && !brand.isEmpty()) {
            candidates.addAll(
                    productRepository.findByBrandOrderByIdDesc(brand).stream()
                            .limit(MAX_CANDIDATES_TO_CHECK)
                            .collect(Collectors.toList()));
        }

        // If we have very few candidates, try category
        if (candidates.size() < 10 && categoryCode != null && !categoryCode.isEmpty()) {
            candidates.addAll(
                    productRepository
                            .findByCategoryOrderByIdDesc(new ProductCategory(categoryCode))
                            .stream()
                            .limit(MAX_CANDIDATES_TO_CHECK)
                            .collect(Collectors.toList()));
        }

        // If we still have very few, try a broader search using model
        if (candidates.size() < 5 && model != null && !model.isEmpty()) {
            candidates.addAll(
                    productRepository.searchProducts(model).stream()
                            .limit(MAX_CANDIDATES_TO_CHECK)
                            .collect(Collectors.toList()));
        }

        return candidates;
    }

    /** Create a new product from a raw item */
    private Product createNewProduct(
            CrawlerRaw rawItem,
            String cleanedTitle,
            String brand,
            String model,
            String categoryCode) {
        Product product = new Product();

        // Use a more consistent product name by constructing it from brand and model
        if (brand != null && model != null) {
            product.setName(brand + " " + model);
        } else {
            // Fallback to the cleaned title if brand or model is missing
            product.setName(cleanedTitle);
        }

        product.setBrand(brand);
        product.setModel(model);

        // Try to find a matching product category
        Optional<ProductCategory> categoryOpt = productCategoryRepository.findById(categoryCode);
        categoryOpt.ifPresent(product::setCategory);

        return productRepository.save(product);
    }

    /** Add a variant to an existing product */
    private void addVariantToProduct(
            Product product,
            CrawlerRaw rawItem,
            String color,
            String storageInfo,
            String property1) {
        // Get the website from the crawler job
        CrawlerWebsite website = rawItem.getJob().getCrawlerWebsite();
        String sourceUrl = rawItem.getLink();

        Optional<ProductVariant> existingVariant =
                productVariantRepository.findByProductAndSourceUrl(product, sourceUrl);

        if (existingVariant.isPresent()) {
            // Update existing variant
            ProductVariant variant = existingVariant.get();

            // Update price and other info that might change
            variant.setPrice(rawItem.getPrice());
            variant.setOldPrice(rawItem.getOldPrice());
            variant.setDiscount(rawItem.getDiscount());
            variant.setPriceString(rawItem.getPriceString());

            productVariantRepository.save(variant);
        } else {
            // Create new variant
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setWebsite(website);
            variant.setSourceUrl(sourceUrl);
            variant.setTitle(rawItem.getTitle());
            variant.setColor(color);

            // Set the storage info as size for smartphones
            variant.setSize(storageInfo);
            variant.setProperty1(property1);

            variant.setPrice(rawItem.getPrice());
            variant.setOldPrice(rawItem.getOldPrice());
            variant.setDiscount(rawItem.getDiscount());
            variant.setPriceString(rawItem.getPriceString());
            variant.setRawProductId(rawItem.getId());

            productVariantRepository.save(variant);
        }
    }

    /** Calculate similarity between a product and extracted attributes */
    private double calculateProductSimilarity(Product product, String brand, String model, String cleanedTitle) {
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
     * Calculate similarity between model strings with context awareness
     */
    private double calculateContextAwareModelSimilarity(String model1, String model2) {
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
        int uniqueTokenCount = uniqueTokens1.size() + uniqueTokens2.size();

        // Calculate similarity based on common tokens
        double commonTokenScore = totalTokens > 0 ? (2.0 * commonTokens.size()) / totalTokens : 0;

        // Apply a penalty based on the number of unique tokens
        if (uniqueTokenCount > 0) {
            double penaltyFactor = Math.min(0.7, 1.0 - (0.2 * uniqueTokenCount));
            return commonTokenScore * penaltyFactor;
        }

        return commonTokenScore;
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

    /**
     * Extract key search term from title for faster matching
     */
    private String getKeySearchTerm(String cleanedTitle) {
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

    /**
     * Extract category from the config code
     */
    private String extractCategory(String configCode) {
        if (configCode == null || configCode.isEmpty()) {
            return "unknown";
        }

        // Try to get category from format like "website.com/category"
        String[] parts = configCode.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }

        return configCode;
    }

    /**
     * Manual trigger method for API endpoints
     */
    @Transactional
    public void manualProcessUnprocessedItems() {
        processNewRawProducts();
    }

    /**
     * Process items for a specific category
     */
    @Transactional
    public void processItemsByCategory(String category) {
        List<CrawlerRaw> unprocessedItems =
                crawlerRawRepository.findByProcessedFalseAndConfigCodeContaining(category);
        logger.info(
                "Found {} unprocessed raw products for category {}",
                unprocessedItems.size(),
                category);

        int processed = 0;
        int skipped = 0;
        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                boolean mapped = processRawProduct(rawItem);
                if (mapped) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                logger.error(
                        "Error processing raw product with id {}: {}",
                        rawItem.getId(),
                        e.getMessage(),
                        e);

                // Track the error
                trackUnmappableItem(
                        rawItem,
                        UnmappableItem.ReasonCode.OTHER,
                        "Error processing: " + e.getMessage());
            }
        }

        logger.info(
                "Completed processing {} raw products for category {} ({} skipped and tracked)",
                processed,
                category,
                skipped);
    }

    /**
     * Retry processing items that were previously unmappable
     */
    @Transactional
    public void retryUnmappableItems(int maxAttempts) {
        List<UnmappableItem> itemsToRetry =
                unmappableItemRepository.findByAttemptsLessThan(maxAttempts);

        logger.info("Attempting to reprocess {} previously unmappable items", itemsToRetry.size());

        int mapped = 0;
        for (UnmappableItem unmappableItem : itemsToRetry) {
            Optional<CrawlerRaw> rawItemOpt =
                    crawlerRawRepository.findById(unmappableItem.getRawItemId());

            if (rawItemOpt.isPresent()) {
                CrawlerRaw rawItem = rawItemOpt.get();
                try {
                    boolean success = processRawProduct(rawItem);
                    if (success) {
                        mapped++;
                        // Item was successfully mapped, so remove from unmappable tracking
                        unmappableItemRepository.delete(unmappableItem);
                    } else {
                        // Update attempt count
                        unmappableItem.setAttempts(unmappableItem.getAttempts() + 1);
                        unmappableItemRepository.save(unmappableItem);
                    }
                } catch (Exception e) {
                    logger.error(
                            "Error retrying unmappable item {}: {}",
                            unmappableItem.getRawItemId(),
                            e.getMessage());
                    // Update the tracking with the new error
                    trackUnmappableItem(
                            rawItem,
                            UnmappableItem.ReasonCode.OTHER,
                            "Error during retry: " + e.getMessage());
                }
            }
        }

        logger.info(
                "Reprocessed {} previously unmappable items, {} were successfully mapped",
                itemsToRetry.size(),
                mapped);
    }

    /**
     * Add new brands to registry based on analysis
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
            boolean exists = productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.BRAND);

            if (exists) {
                logger.info("Brand '{}' already exists in registry", normalizedBrand);
                continue;
            }

            // Capitalize the first letter
            String formattedBrand = textProcessor.capitalizeFirstLetter(brand.trim());

            // Create a new registry entry
            ProductRegistry registryEntry = new ProductRegistry();
            registryEntry.setRegistryType(ProductRegistry.RegistryType.BRAND);
            registryEntry.setRegistryKey(formattedBrand);
            registryEntry.setDescription(
                    description != null ? description : "Auto-added from unmappable item analysis");
            registryEntry.setEnabled(true);

            try {
                // Save to repository
                productRegistryRepository.save(registryEntry);
                added++;

                logger.info("Added new brand '{}' to registry", formattedBrand);
            } catch (Exception e) {
                logger.error(
                        "Error adding potential brand '{}' to registry: {}",
                        formattedBrand,
                        e.getMessage());
            }
        }

        // Refresh the registry cache if any brands were added
        if (added > 0) {
            textProcessor.refreshRegistry();
        }

        return added;
    }

    /**
     * Find potential duplicate products
     */
    public List<Map<String, Object>> findPotentialDuplicates() {
        List<Map<String, Object>> results = new ArrayList<>();

        // Get all products with non-null brand and model
        List<Product> products = productRepository.findByBrandNotNullAndModelNotNull();

        Map<String, List<Product>> brandGroups = new HashMap<>();

        // Group products by brand
        for (Product product : products) {
            String brand = product.getBrand().toLowerCase();
            brandGroups.computeIfAbsent(brand, k -> new ArrayList<>()).add(product);
        }

        // For each brand group, look for potential model duplicates
        for (Map.Entry<String, List<Product>> entry : brandGroups.entrySet()) {
            String brand = entry.getKey();
            List<Product> brandProducts = entry.getValue();

            // Skip if only one product with this brand
            if (brandProducts.size() <= 1) {
                continue;
            }

            // Check each product against others in the same brand
            for (int i = 0; i < brandProducts.size(); i++) {
                Product product1 = brandProducts.get(i);

                for (int j = i + 1; j < brandProducts.size(); j++) {
                    Product product2 = brandProducts.get(j);

                    // Skip if either product doesn't have a model
                    if (product1.getModel() == null || product2.getModel() == null) {
                        continue;
                    }

                    // Calculate model similarity
                    double similarity = calculateContextAwareModelSimilarity(
                            product1.getModel(), product2.getModel());

                    // If high similarity, consider them potential duplicates
                    if (similarity >= MERGE_SIMILARITY_THRESHOLD) {
                        Map<String, Object> duplicateInfo = new HashMap<>();
                        duplicateInfo.put("product1Id", product1.getId());
                        duplicateInfo.put("product1Name", product1.getName());
                        duplicateInfo.put("product1Model", product1.getModel());
                        duplicateInfo.put("product2Id", product2.getId());
                        duplicateInfo.put("product2Name", product2.getName());
                        duplicateInfo.put("product2Model", product2.getModel());
                        duplicateInfo.put("brand", brand);
                        duplicateInfo.put("similarity", similarity);
                        duplicateInfo.put("variantCount1", productVariantRepository.countByProductId(product1.getId()));
                        duplicateInfo.put("variantCount2", productVariantRepository.countByProductId(product2.getId()));

                        results.add(duplicateInfo);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Merge two products that are duplicates
     */
    @Transactional
    public boolean mergeProducts(Integer sourceProductId, Integer targetProductId) {
        // Get both products
        Optional<Product> sourceOpt = productRepository.findById(sourceProductId);
        Optional<Product> targetOpt = productRepository.findById(targetProductId);

        if (!sourceOpt.isPresent() || !targetOpt.isPresent()) {
            logger.error("Cannot merge products: source or target product not found");
            return false;
        }

        Product sourceProduct = sourceOpt.get();
        Product targetProduct = targetOpt.get();

        // Move all variants from source to target
        List<ProductVariant> variants = productVariantRepository.findByProductId(sourceProductId);
        int movedVariants = 0;

        for (ProductVariant variant : variants) {
            // Check if this variant would duplicate an existing one
            Optional<ProductVariant> existingVariant =
                    productVariantRepository.findByProductAndSourceUrl(targetProduct, variant.getSourceUrl());

            if (existingVariant.isPresent()) {
                // Update the existing variant with latest info
                ProductVariant existing = existingVariant.get();
                existing.setPrice(variant.getPrice());
                existing.setOldPrice(variant.getOldPrice());
                existing.setDiscount(variant.getDiscount());
                existing.setPriceString(variant.getPriceString());
                productVariantRepository.save(existing);

                // Delete the redundant variant
                productVariantRepository.delete(variant);
            } else {
                // Move variant to target product
                variant.setProduct(targetProduct);
                productVariantRepository.save(variant);
                movedVariants++;
            }
        }

        // Update any raw items pointing to the source product
        crawlerRawRepository.updateMatchedProductId(sourceProductId, targetProductId);

        // Log the merge
        logger.info("Merged product {} ({}) into product {} ({}), moved {} variants",
                sourceProductId, sourceProduct.getName(),
                targetProductId, targetProduct.getName(),
                movedVariants);

        // Delete the source product
        productRepository.delete(sourceProduct);

        return true;
    }
}