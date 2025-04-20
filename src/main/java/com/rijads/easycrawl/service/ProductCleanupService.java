package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.*;
import com.rijads.easycrawl.repository.*;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for cleaning and maintaining product data consistency.
 * Updated to work with the job system.
 */
@Service
public class ProductCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(ProductCleanupService.class);
    private static final double MERGE_SIMILARITY_THRESHOLD = 0.85; // Threshold for merging products

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CrawlerRawRepository crawlerRawRepository;

    @Autowired
    private ProductTextProcessor textProcessor;

    @Autowired
    private ProductRegistryRepository productRegistryRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobErrorRepository jobErrorRepository;

    /**
     * Process a product cleanup job
     * This handles the cleanup operations based on the job parameters
     */
    @Transactional
    public void processCleanupJob(Job job) {
        logger.info("Starting product cleanup for job {}", job.getId());

        try {
            // Determine what cleanup operations to perform based on parameters
            String parameters = job.getParameters();
            Map<String, Object> results = new HashMap<>();

            if (parameters == null || parameters.isEmpty() || parameters.contains("all")) {
                // Do both name updates and duplicate merging
                Map<String, Object> nameResults = updateProductNamesBasedOnRegistry();
                Map<String, Object> mergeResults = mergeProductDuplicates();

                results.putAll(nameResults);
                results.putAll(mergeResults);

                logger.info("Completed full product cleanup. Updated {} products, merged {} duplicates",
                        nameResults.get("updatedCount"), mergeResults.get("mergedCount"));
            } else if (parameters.contains("names")) {
                // Only do name updates
                results = updateProductNamesBasedOnRegistry();
                logger.info("Completed product name updates. Updated {} products",
                        results.get("updatedCount"));
            } else if (parameters.contains("duplicates")) {
                // Only do duplicate merging
                results = mergeProductDuplicates();
                logger.info("Completed duplicate merging. Merged {} products",
                        results.get("mergedCount"));
            }

            // Store results in the job parameters field
            job.setParameters(job.getParameters() + " | Results: " +
                    "updated=" + results.getOrDefault("updatedCount", 0) +
                    ", merged=" + results.getOrDefault("mergedCount", 0));

        } catch (Exception e) {
            logger.error("Error during product cleanup job {}: {}", job.getId(), e.getMessage(), e);

            // Record error
            JobError error = new JobError();
            error.setJob(job);
            error.setSource("system");
            error.setCategory("product-cleanup");
            error.setJobType("PRODUCT_CLEANUP");
            error.setError(e.getMessage());
            error.setCreated(LocalDateTime.now());
            jobErrorRepository.save(error);

            throw e;
        }
    }

    /**
     * Updates all products' brand and model names based on current registry rules
     * Uses the same text processing logic as the product matching service
     */
    @Transactional
    public Map<String, Object> updateProductNamesBasedOnRegistry() {
        logger.info("Starting product name update based on registry");

        int updatedCount = 0;
        List<Map<String, Object>> updatedProducts = new ArrayList<>();

        // Process all products
        List<Product> allProducts = Streamable.of(productRepository.findAll()).toList();
        logger.info("Processing {} products for name standardization", allProducts.size());

        for (Product product : allProducts) {
            boolean updated = false;
            Map<String, Object> updateInfo = new HashMap<>();

            // Store original values for logging
            String originalName = product.getName();
            String originalBrand = product.getBrand();
            String originalModel = product.getModel();

            // Only process products that have a title to work with
            if (product.getName() == null || product.getName().isEmpty()) {
                continue;
            }

            // Clean and re-extract information using the latest registry rules
            String cleanedTitle = textProcessor.cleanTitle(product.getName());

            // If brand is missing or might be incorrect, try to re-extract it
            String newBrand;
            if (product.getBrand() == null || product.getBrand().isEmpty()) {
                newBrand = textProcessor.extractBrand(product.getName());
            } else {
                // Keep existing brand but check if its still valid in registry
                newBrand = textProcessor.extractBrand(product.getName());
                if (newBrand == null || newBrand.isEmpty()) {
                    // If brand can't be found in registry but we have one, keep it
                    newBrand = product.getBrand();
                }
            }

            // Extract and standardize model
            String extractedModel = textProcessor.extractModel(cleanedTitle, newBrand);

            // Standardize model name
            String standardizedModel = textProcessor.standardizeModelName(newBrand, extractedModel);

            // Update brand if it changed
            if (newBrand != null && !newBrand.equals(product.getBrand())) {
                product.setBrand(newBrand);
                updated = true;
                updateInfo.put("brandFrom", originalBrand);
                updateInfo.put("brandTo", newBrand);
            }

            // Update model if it changed
            if (standardizedModel != null && !standardizedModel.equals(product.getModel())) {
                product.setModel(standardizedModel);
                updated = true;
                updateInfo.put("modelFrom", originalModel);
                updateInfo.put("modelTo", standardizedModel);
            }

            // Create consistent product name from brand and model
            if (newBrand != null && standardizedModel != null) {
                String newName = newBrand + " " + standardizedModel;
                if (!newName.equals(product.getName())) {
                    product.setName(newName);
                    updated = true;
                    updateInfo.put("nameFrom", originalName);
                    updateInfo.put("nameTo", newName);
                }
            }

            // Save if updated
            if (updated) {
                productRepository.save(product);
                updatedCount++;

                updateInfo.put("productId", product.getId());
                updateInfo.put("timestamp", LocalDateTime.now());
                updatedProducts.add(updateInfo);

                // Log every 100 updates for visibility
                if (updatedCount % 100 == 0) {
                    logger.info("Processed {} products, updated {}", allProducts.indexOf(product) + 1, updatedCount);
                }
            }
        }

        logger.info("Completed product name updates. Updated {} out of {} products.",
                updatedCount, allProducts.size());

        return Map.of(
                "updatedCount", updatedCount,
                "updatedProducts", updatedProducts
        );
    }

    /**
     * Detects and merges duplicate products
     * Uses the same similarity detection as the product matching service
     */
    @Transactional
    public Map<String, Object> mergeProductDuplicates() {
        logger.info("Starting duplicate product detection and merging");

        int mergedCount = 0;
        List<Map<String, Object>> mergedProducts = new ArrayList<>();

        // Get all products with non-null brand and model
        List<Product> products = productRepository.findByBrandNotNullAndModelNotNull();
        logger.info("Found {} products with brand and model for duplicate analysis", products.size());

        // Group products by brand for more efficient processing
        Map<String, List<Product>> brandGroups = new HashMap<>();
        for (Product product : products) {
            String brand = product.getBrand().toLowerCase();
            brandGroups.computeIfAbsent(brand, k -> new ArrayList<>()).add(product);
        }

        // Process each brand group
        for (Map.Entry<String, List<Product>> entry : brandGroups.entrySet()) {
            String brand = entry.getKey();
            List<Product> brandProducts = entry.getValue();

            // Skip if only one product with this brand
            if (brandProducts.size() <= 1) {
                continue;
            }

            // Track products that have been merged to avoid reprocessing
            Set<Integer> mergedProductIds = new HashSet<>();

            // Check each product against others in the same brand
            for (int i = 0; i < brandProducts.size(); i++) {
                Product product1 = brandProducts.get(i);

                // Skip if this product was already merged into another
                if (mergedProductIds.contains(product1.getId())) {
                    continue;
                }

                for (int j = i + 1; j < brandProducts.size(); j++) {
                    Product product2 = brandProducts.get(j);

                    // Skip if this product was already merged
                    if (mergedProductIds.contains(product2.getId())) {
                        continue;
                    }

                    // Skip if either product doesn't have a model
                    if (product1.getModel() == null || product2.getModel() == null) {
                        continue;
                    }

                    // Calculate model similarity
                    double similarity = calculateModelSimilarity(
                            product1.getModel(), product2.getModel());

                    // If high similarity, merge the products
                    if (similarity >= MERGE_SIMILARITY_THRESHOLD) {
                        // Determine which product to keep (the one with most variants)
                        long variantCount1 = productVariantRepository.countByProductId(product1.getId());
                        long variantCount2 = productVariantRepository.countByProductId(product2.getId());

                        Product sourceProduct, targetProduct;
                        if (variantCount1 >= variantCount2) {
                            // Keep product1, merge product2 into it
                            targetProduct = product1;
                            sourceProduct = product2;
                        } else {
                            // Keep product2, merge product1 into it
                            targetProduct = product2;
                            sourceProduct = product1;
                        }

                        // Perform the merge
                        boolean merged = mergeProducts(sourceProduct.getId(), targetProduct.getId());

                        if (merged) {
                            mergedCount++;
                            mergedProductIds.add(sourceProduct.getId());

                            // Log the merge
                            Map<String, Object> mergeInfo = new HashMap<>();
                            mergeInfo.put("sourceId", sourceProduct.getId());
                            mergeInfo.put("sourceName", sourceProduct.getName());
                            mergeInfo.put("targetId", targetProduct.getId());
                            mergeInfo.put("targetName", targetProduct.getName());
                            mergeInfo.put("similarity", similarity);
                            mergeInfo.put("timestamp", LocalDateTime.now());
                            mergedProducts.add(mergeInfo);

                            logger.info("Merged duplicate products: {} into {}",
                                    sourceProduct.getName(), targetProduct.getName());
                        }
                    }
                }
            }
        }

        logger.info("Completed duplicate merging. Merged {} products.", mergedCount);

        return Map.of(
                "mergedCount", mergedCount,
                "mergedProducts", mergedProducts
        );
    }

    /**
     * Calculate similarity between two model strings
     * Uses token-based similarity with numerical equivalence awareness
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
     * e.g., "14" and "14" are equivalent, as are "S21" and "S-21"
     */
    private boolean isNumericallyEquivalent(String token1, String token2) {
        // Extract numerical parts
        String num1 = token1.replaceAll("[^0-9]", "");
        String num2 = token2.replaceAll("[^0-9]", "");

        // Extract alphabetical parts
        String alpha1 = token1.replaceAll("[^a-zA-Z]", "").toLowerCase();
        String alpha2 = token2.replaceAll("[^a-zA-Z]", "").toLowerCase();

        // Both numerical and alphabetical parts should match
        boolean numbersMatch = !num1.isEmpty() && !num2.isEmpty() && num1.equals(num2);
        boolean alphaMatch = !alpha1.isEmpty() && !alpha2.isEmpty() && alpha1.equals(alpha2);

        return (numbersMatch && alpha1.isEmpty() && alpha2.isEmpty()) || // Pure numbers match
                (alphaMatch && num1.isEmpty() && num2.isEmpty()) ||       // Pure letters match
                (numbersMatch && alphaMatch);                             // Both parts match
    }

    /**
     * Merge two products, moving all variants from source to target
     */
    @Transactional
    protected boolean mergeProducts(Integer sourceProductId, Integer targetProductId) {
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

    /**
     * Manual trigger for product cleanup
     * Updated to create and use a job entry
     */
    @Transactional
    public Map<String, Object> manualProductCleanup() {
        // Create a new job
        Job job = new Job();
        job.setStatus("Running");
        job.setJobType("PRODUCT_CLEANUP");
        job.setCreated(LocalDateTime.now());
        job.setCreatedBy("API");
        job.setStartedAt(LocalDateTime.now());
        job.setParameters("all-manual");
        job = jobRepository.save(job);

        Map<String, Object> results = new HashMap<>();

        try {
            // Perform cleanup
            Map<String, Object> nameResults = updateProductNamesBasedOnRegistry();
            Map<String, Object> mergeResults = mergeProductDuplicates();

            results.putAll(nameResults);
            results.putAll(mergeResults);

            // Mark job as finished
            job.setStatus("Finished");
            job.setFinishedAt(LocalDateTime.now());
            job.setParameters(job.getParameters() + " | Results: " +
                    "updated=" + results.getOrDefault("updatedCount", 0) +
                    ", merged=" + results.getOrDefault("mergedCount", 0));
            jobRepository.save(job);

        } catch (Exception e) {
            // Mark job as failed
            job.setStatus("Failed");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);

            // Record error
            JobError error = new JobError();
            error.setJob(job);
            error.setSource("system");
            error.setCategory("product-cleanup");
            error.setJobType("PRODUCT_CLEANUP");
            error.setError(e.getMessage());
            error.setCreated(LocalDateTime.now());
            jobErrorRepository.save(error);

            throw e;
        }

        return results;
    }
}