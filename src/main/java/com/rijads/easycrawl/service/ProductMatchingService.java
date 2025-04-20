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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(ProductMatchingService.class);
    private static final int MAX_SIMILAR_ITEMS_TO_CHECK = 20;
    private static final int MAX_CANDIDATES_TO_CHECK = 30;
    private static final double SIMILARITY_THRESHOLD = 0.70;
    private static final double MERGE_SIMILARITY_THRESHOLD = 0.85;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private CrawlerRawRepository crawlerRawRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductRegistryRepository productRegistryRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private ProductTextProcessor textProcessor;
    @Autowired private ProductCategoryRepository productCategoryRepository;
    @Autowired private UnmappableItemRepository unmappableItemRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobErrorRepository jobErrorRepository;

    /**
     * Process raw products based on a job
     * This replaces the scheduled method and is called by the job processor
     */
    @Transactional
    public void processRawProductsForJob(Job job) {
        logger.info("Starting product matching for job {}", job.getId());

        List<CrawlerRaw> unprocessedItems;

        // Check if we have a specific category from job parameters or config
        String categoryCode = null;
        if (job.getConfig() != null && job.getConfig().getProductCategory() != null) {
            categoryCode = job.getConfig().getProductCategory().getCode();
        } else if (job.getParameters() != null && !job.getParameters().isEmpty()) {
            categoryCode = job.getParameters();
        }

        // Get items to process based on category
        if (categoryCode != null) {
            unprocessedItems = crawlerRawRepository.findByProcessedFalseAndConfigCodeContaining(categoryCode);
            logger.info("Found {} unprocessed raw products for category {}",
                    unprocessedItems.size(), categoryCode);
        } else {
            unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();
            logger.info("Found {} unprocessed raw products", unprocessedItems.size());
        }

        int processed = 0;
        int skipped = 0;

        // Process raw items
        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                boolean mapped = processRawProduct(rawItem);

                if (mapped) {
                    processed++;
                } else {
                    skipped++;
                }

                // Log progress periodically
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
                // Log the error
                trackUnmappableItem(
                        rawItem,
                        UnmappableItem.ReasonCode.OTHER,
                        "Error processing: " + e.getMessage());

                // Record error in job_error table
                String source = rawItem.getConfigCode().split("/")[0];
                String category = extractCategory(rawItem.getConfigCode());
                createJobError(job, source, category, e);
            }
        }

        logger.info(
                "Completed processing {} raw products ({} skipped and tracked)",
                processed,
                skipped);
    }

    /**
     * Record an error for a job in the job_error table
     */
    private void createJobError(Job job, String source, String category, Exception e) {
        JobError error = new JobError();
        error.setJob(job);
        error.setSource(source);
        error.setCategory(category);
        error.setJobType("PRODUCT_MAPPING");
        error.setError(e.getMessage());
        error.setCreated(LocalDateTime.now());
        jobErrorRepository.save(error);
    }

    /**
     * Manual trigger method for API endpoints
     * Updated to create and use a job entry
     */
    @Transactional
    public void manualProcessUnprocessedItems() {
        // Create a new job
        Job job = new Job();
        job.setStatus("Running");
        job.setJobType("PRODUCT_MAPPING");
        job.setCreated(LocalDateTime.now());
        job.setCreatedBy("API");
        job.setStartedAt(LocalDateTime.now());
        job = jobRepository.save(job);

        try {
            processRawProductsForJob(job);

            // Mark job as finished
            job.setStatus("Finished");
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
        } catch (Exception e) {
            // Mark job as failed
            job.setStatus("Failed");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);

            // Record the error
            createJobError(job, "system", "all", e);
            throw e;
        }
    }

    /**
     * Process items for a specific category with detailed results tracking
     * Used by the job processor service
     *
     * @param category The product category code to process
     * @param job The job object for tracking progress and results
     * @return The number of newly mapped products
     */
    @Transactional
    public int processItemsByCategory(String category, Job job) {
        if (category == null || category.isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or empty");
        }

        StringBuilder resultDescription = new StringBuilder();
        resultDescription.append("Processing items for category: ").append(category).append("\n\n");

        List<CrawlerRaw> unprocessedItems =
                crawlerRawRepository.findByProcessedFalseAndConfigCodeContaining(category);

        resultDescription.append("Found ").append(unprocessedItems.size())
                .append(" unprocessed items\n\n");

        logger.info(
                "Found {} unprocessed raw products for category {}",
                unprocessedItems.size(),
                category);

        int processed = 0;
        int skipped = 0;
        List<String> newProductNames = new ArrayList<>();
        Map<String, Integer> brandCounts = new HashMap<>();

        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                boolean mapped = processRawProduct(rawItem);
                if (mapped) {
                    processed++;

                    // Track the product that was mapped
                    if (rawItem.getMatchedProductId() != null) {
                        Optional<Product> product = productRepository.findById(rawItem.getMatchedProductId());
                        if (product.isPresent()) {
                            String productName = product.get().getName();
                            if (product.get().getBrand() != null) {
                                brandCounts.put(product.get().getBrand(),
                                        brandCounts.getOrDefault(product.get().getBrand(), 0) + 1);
                            }

                            // Only add to the list for display if it's a new product
                            if (!newProductNames.contains(productName)) {
                                newProductNames.add(productName);
                            }
                        }
                    }
                } else {
                    skipped++;
                }

                // Update job description periodically for progress tracking
                if ((processed + skipped) % 50 == 0) {
                    StringBuilder progressUpdate = new StringBuilder(resultDescription);
                    progressUpdate.append("Progress: Processed ").append(processed + skipped)
                            .append(" of ").append(unprocessedItems.size())
                            .append(" (").append(processed).append(" mapped, ")
                            .append(skipped).append(" skipped)\n");

                    job.setDescription(progressUpdate.toString());
                    jobRepository.save(job);
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

                skipped++;
            }
        }

        // Build final results summary
        resultDescription.append("== RESULTS SUMMARY ==\n");
        resultDescription.append("Total processed: ").append(processed + skipped).append("\n");
        resultDescription.append("Successfully mapped: ").append(processed).append("\n");
        resultDescription.append("Skipped/unmappable: ").append(skipped).append("\n\n");

        // Add information about brands
        if (!brandCounts.isEmpty()) {
            resultDescription.append("== BRANDS SUMMARY ==\n");
            brandCounts.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry ->
                            resultDescription.append(entry.getKey()).append(": ")
                                    .append(entry.getValue()).append("\n"));
            resultDescription.append("\n");
        }

        // Add list of new products (limited to 100 to avoid huge descriptions)
        if (!newProductNames.isEmpty()) {
            resultDescription.append("== NEW PRODUCTS ==\n");
            newProductNames.stream().limit(100).forEach(name ->
                    resultDescription.append("- ").append(name).append("\n"));

            if (newProductNames.size() > 100) {
                resultDescription.append("... and ").append(newProductNames.size() - 100)
                        .append(" more products\n");
            }
        }

        logger.info(
                "Completed processing {} raw products for category {} ({} skipped and tracked)",
                processed,
                category,
                skipped);

        // Save the final description to the job
        job.setDescription(resultDescription.toString());
        jobRepository.save(job);

        return processed;
    }

    /**
     * Process all unmapped items across all categories
     * Used by the job processor service
     *
     * @param job The job object for tracking progress and results
     * @return The number of newly mapped products
     */
    @Transactional
    public int processAllUnmappedItems(Job job) {
        StringBuilder resultDescription = new StringBuilder();
        resultDescription.append("Processing all unmapped items\n\n");

        List<CrawlerRaw> unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();

        resultDescription.append("Found ").append(unprocessedItems.size())
                .append(" unprocessed items\n\n");

        logger.info("Found {} unprocessed raw products", unprocessedItems.size());

        int processed = 0;
        int skipped = 0;
        Map<String, Integer> categoryStats = new HashMap<>();
        Map<String, Integer> brandCounts = new HashMap<>();
        List<String> newProductNames = new ArrayList<>();

        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                // Extract category for statistics
                String category = extractCategory(rawItem.getConfigCode());
                categoryStats.put(category, categoryStats.getOrDefault(category, 0) + 1);

                boolean mapped = processRawProduct(rawItem);
                if (mapped) {
                    processed++;

                    // Track the product that was mapped
                    if (rawItem.getMatchedProductId() != null) {
                        Optional<Product> product = productRepository.findById(rawItem.getMatchedProductId());
                        if (product.isPresent()) {
                            String productName = product.get().getName();
                            if (product.get().getBrand() != null) {
                                brandCounts.put(product.get().getBrand(),
                                        brandCounts.getOrDefault(product.get().getBrand(), 0) + 1);
                            }

                            // Only add to the list for display if it's a new product
                            if (!newProductNames.contains(productName)) {
                                newProductNames.add(productName);
                            }
                        }
                    }
                } else {
                    skipped++;
                }

                // Update job description periodically for progress tracking
                if ((processed + skipped) % 100 == 0) {
                    StringBuilder progressUpdate = new StringBuilder(resultDescription);
                    progressUpdate.append("Progress: Processed ").append(processed + skipped)
                            .append(" of ").append(unprocessedItems.size())
                            .append(" (").append(processed).append(" mapped, ")
                            .append(skipped).append(" skipped)\n");

                    job.setDescription(progressUpdate.toString());
                    jobRepository.save(job);
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

                skipped++;
            }
        }

        // Build final results summary
        resultDescription.append("== RESULTS SUMMARY ==\n");
        resultDescription.append("Total processed: ").append(processed + skipped).append("\n");
        resultDescription.append("Successfully mapped: ").append(processed).append("\n");
        resultDescription.append("Skipped/unmappable: ").append(skipped).append("\n\n");

        // Add category statistics
        if (!categoryStats.isEmpty()) {
            resultDescription.append("== CATEGORY STATISTICS ==\n");
            categoryStats.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry ->
                            resultDescription.append(entry.getKey()).append(": ")
                                    .append(entry.getValue()).append("\n"));
            resultDescription.append("\n");
        }

        // Add brand statistics
        if (!brandCounts.isEmpty()) {
            resultDescription.append("== BRANDS SUMMARY ==\n");
            brandCounts.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(20) // Limit to top 20 brands
                    .forEach(entry ->
                            resultDescription.append(entry.getKey()).append(": ")
                                    .append(entry.getValue()).append("\n"));

            if (brandCounts.size() > 20) {
                resultDescription.append("... and ").append(brandCounts.size() - 20)
                        .append(" more brands\n");
            }
            resultDescription.append("\n");
        }

        // Add list of new products (limited to avoid huge descriptions)
        if (!newProductNames.isEmpty()) {
            resultDescription.append("== NEW PRODUCTS (sample) ==\n");
            newProductNames.stream().limit(50).forEach(name ->
                    resultDescription.append("- ").append(name).append("\n"));

            if (newProductNames.size() > 50) {
                resultDescription.append("... and ").append(newProductNames.size() - 50)
                        .append(" more products\n");
            }
        }

        logger.info(
                "Completed processing all raw products: {} mapped, {} skipped",
                processed,
                skipped);

        // Save the final description to the job
        job.setDescription(resultDescription.toString());
        jobRepository.save(job);

        return processed;
    }

    /**
     * Helper method to get a raw item by ID
     * Used by the API for direct processing requests
     */
    public Optional<CrawlerRaw> getRawItemById(Integer id) {
        return crawlerRawRepository.findById(id);
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
            exactMatches = exactMatches.stream()
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
                    double similarity = textProcessor.calculateTitleSimilarity(cleanedTitle, item.getTitle());
                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity;
                        mostSimilar = item;
                    }
                }
            }

            if (mostSimilar != null && mostSimilar.getMatchedProductId() != null) {
                // Get the product this item was mapped to
                Optional<Product> product = productRepository.findById(mostSimilar.getMatchedProductId());
                if (product.isPresent() && product.get().getBrand() != null
                        && !product.get().getBrand().isEmpty()) {
                    return product;
                }
            }
        }

        return Optional.empty();
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
     * Find potential matching products based on brand and model
     */
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

    /**
     * Create a new product from a raw item
     */
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

    /**
     * Add a variant to an existing product
     */
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

    /**
     * Calculate similarity between a product and extracted attributes
     */
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

        // Model similarity (highest weight)
        if (model != null && product.getModel() != null) {
            double modelWeight = 0.5;

            // First check exact match
            if (model.equalsIgnoreCase(product.getModel())) {
                score += 1.0 * modelWeight;
            } else {
                // For non-exact matches, analyze token similarity
                double modelSimilarity = calculateModelSimilarity(model, product.getModel());
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
     * Calculate similarity between model strings
     */
    private double calculateModelSimilarity(String model1, String model2) {
        if (model1 == null || model2 == null) {
            return 0.0;
        }

        // Check for exact match first
        if (model1.equalsIgnoreCase(model2)) {
            return 1.0;
        }

        // Normalize and tokenize models
        String[] tokens1 = model1.toLowerCase().split("\\s+");
        String[] tokens2 = model2.toLowerCase().split("\\s+");

        // Count matching tokens
        int matchingTokens = 0;
        for (String token1 : tokens1) {
            for (String token2 : tokens2) {
                if (token1.equals(token2) || isNumericallyEquivalent(token1, token2)) {
                    matchingTokens++;
                    break;
                }
            }
        }

        // Calculate Dice coefficient
        int totalTokens = tokens1.length + tokens2.length;
        if (totalTokens == 0) {
            return 0.0;
        }

        return (2.0 * matchingTokens) / totalTokens;
    }

    /**
     * Check if two tokens are numerically equivalent
     */
    private boolean isNumericallyEquivalent(String token1, String token2) {
        // Extract numerical parts
        String num1 = token1.replaceAll("[^0-9]", "");
        String num2 = token2.replaceAll("[^0-9]", "");

        // If both have numbers, compare them
        if (!num1.isEmpty() && !num2.isEmpty()) {
            return num1.equals(num2);
        }

        return false;
    }

    /** Extract category from the config code */
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

    /**
     * Retry processing items that were previously unmappable
     * Updated to create and use a job entry
     */
    @Transactional
    public void retryUnmappableItems(int maxAttempts) {
        // Create a new job
        Job job = new Job();
        job.setStatus("Running");
        job.setJobType("PRODUCT_MAPPING");
        job.setCreated(LocalDateTime.now());
        job.setCreatedBy("API");
        job.setStartedAt(LocalDateTime.now());
        job.setParameters("retry-unmappable");
        job = jobRepository.save(job);

        try {
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
                                e.getMessage(),
                                e);
                        // Update the tracking with the new error
                        trackUnmappableItem(
                                rawItem,
                                UnmappableItem.ReasonCode.OTHER,
                                "Error during retry: " + e.getMessage());

                        // Record error in job_error table
                        String source = rawItem.getConfigCode().split("/")[0];
                        String category = extractCategory(rawItem.getConfigCode());
                        createJobError(job, source, category, e);
                    }
                }
            }

            logger.info(
                    "Reprocessed {} previously unmappable items, {} were successfully mapped",
                    itemsToRetry.size(),
                    mapped);

            // Mark job as finished
            job.setStatus("Finished");
            job.setFinishedAt(LocalDateTime.now());
            job.setDescription("Processed " + itemsToRetry.size() + " unmappable items, " + mapped + " were successfully mapped");
            jobRepository.save(job);
        } catch (Exception e) {
            // Mark job as failed
            job.setStatus("Failed");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);

            // Record the error
            createJobError(job, "system", "all", e);
            throw e;
        }
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
     * Process a single raw product
     * Core method that handles the matching and mapping logic
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
        String model = textProcessor.extractModel(rawItem.getTitle(), brand);
        String color = textProcessor.extractColor(rawItem.getTitle());
        String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());
        String property1 = "";
        if (categoryCode.equalsIgnoreCase("smartphones")) {
            property1 = textProcessor.extractRamInfo(rawItem.getTitle());
        }

        // Find potential matching products
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
            Product newProduct = createNewProduct(rawItem, cleanedTitle, brand, model, categoryCode);
            addVariantToProduct(newProduct, rawItem, color, storageInfo, property1);
            rawItem.setMatchedProductId(newProduct.getId());
            rawItem.setProcessed(true);

            // Log that we created a new product
            logger.info(
                    "Created new product for '{}' with brand '{}'",
                    rawItem.getTitle(),
                    brand);

            // If this item was previously unmappable, remove it from the unmappable tracking
            unmappableItemRepository.findById(rawItem.getId())
                    .ifPresent(unmappableItemRepository::delete);
        }

        return true;
    }
}