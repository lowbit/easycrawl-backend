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

@Service
public class ProductMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(ProductMatchingService.class);
    private static final int MAX_CANDIDATES_TO_CHECK = 30;
    private static final double SIMILARITY_THRESHOLD = 0.70;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private CrawlerRawRepository crawlerRawRepository;
    @Autowired private ProductTextProcessor textProcessor;
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
     * Process items for a specific category
     * Updated to create and use a job entry
     */
    @Transactional
    public void processItemsByCategory(String category) {
        // Create a new job
        Job job = new Job();
        job.setStatus("Running");
        job.setJobType("PRODUCT_MAPPING");
        job.setCreated(LocalDateTime.now());
        job.setCreatedBy("API");
        job.setStartedAt(LocalDateTime.now());
        job.setParameters(category);
        job = jobRepository.save(job);

        try {
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

                    // Record error in job_error table
                    String source = rawItem.getConfigCode().split("/")[0];
                    createJobError(job, source, category, e);
                }
            }

            logger.info(
                    "Completed processing {} raw products for category {} ({} skipped and tracked)",
                    processed,
                    category,
                    skipped);

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
            createJobError(job, "system", category, e);
            throw e;
        }
    }

    /**
     * Process a single raw product
     * This method is kept unchanged but is now called from the job-based methods
     */
    @Transactional
    public boolean processRawProduct(CrawlerRaw rawItem) {
        // Original implementation remains unchanged
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

    // The rest of the class remains unchanged
    // ... (all helper methods like findSimilarMappedProductFast, trackUnmappableItem, etc.)

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

    private Optional<Product> findSimilarMappedProductFast(
            CrawlerRaw rawItem, String cleanedTitle, String categoryCode) {
        // Existing implementation remains unchanged
        return Optional.empty(); // Placeholder for brevity
    }

    private List<Product> findCandidateProducts(String brand, String model, String categoryCode) {
        // Existing implementation remains unchanged
        return Collections.emptyList(); // Placeholder for brevity
    }

    private Product createNewProduct(
            CrawlerRaw rawItem,
            String cleanedTitle,
            String brand,
            String model,
            String categoryCode) {
        // Existing implementation remains unchanged
        Product product = new Product();
        // Initialize fields
        return product; // Placeholder for brevity
    }

    private void addVariantToProduct(
            Product product,
            CrawlerRaw rawItem,
            String color,
            String storageInfo,
            String property1) {
        // Existing implementation remains unchanged
    }

    private double calculateProductSimilarity(Product product, String brand, String model, String cleanedTitle) {
        // Existing implementation remains unchanged
        return 0.0; // Placeholder for brevity
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
        // Existing implementation remains unchanged
        return 0; // Placeholder for brevity
    }

    //getRawItemById
    public Optional<CrawlerRaw> getRawItemById(Integer itemId) {
        return crawlerRawRepository.findById(itemId);
    }
}