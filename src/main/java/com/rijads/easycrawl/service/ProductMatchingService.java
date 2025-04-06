package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.*;
import com.rijads.easycrawl.repository.CrawlerRawRepository;
import com.rijads.easycrawl.repository.ProductCategoryRepository;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(ProductMatchingService.class);
    private static final double SIMILARITY_THRESHOLD = 0.7;

    @Autowired
    private CrawlerRawRepository crawlerRawRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductTextProcessor textProcessor;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    /**
     * Scheduled job to process new raw products
     */
    @Scheduled(fixedRate = 900000) // Every 15 minutes
    @Transactional
    public void processNewRawProducts() {
        logger.info("Starting scheduled product processing job");

        List<CrawlerRaw> unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();
        logger.info("Found {} unprocessed raw products", unprocessedItems.size());

        int processed = 0;
        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                processRawProduct(rawItem);
                processed++;

                // Process in smaller batches to avoid long transactions
                if (processed % 50 == 0) {
                    logger.info("Processed {} of {} raw products", processed, unprocessedItems.size());
                }
            } catch (Exception e) {
                logger.error("Error processing raw product with id {}: {}", rawItem.getId(), e.getMessage(), e);
            }
        }

        logger.info("Completed processing {} raw products", processed);
    }

    /**
     * Process a single raw product
     */
    @Transactional
    public void processRawProduct(CrawlerRaw rawItem) {
        // Skip if already processed
        if (Boolean.TRUE.equals(rawItem.getProcessed())) {
            return;
        }

        // Extract category from config code (e.g., "domod.ba/smartphones" -> "smartphones")
        String categoryCode = extractCategory(rawItem.getConfigCode());

        // Clean and extract product info
        String cleanedTitle = textProcessor.cleanTitle(rawItem.getTitle());
        String brand = textProcessor.extractBrand(rawItem.getTitle());
        String model = textProcessor.extractModel(rawItem.getTitle(), brand);
        String color = textProcessor.extractColor(rawItem.getTitle());
        String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());

        // Find potential matching products
        List<Product> candidates = findCandidateProducts(brand, model, categoryCode);

        Product bestMatch = null;
        double highestSimilarity = 0;

        // Find best match among candidates
        for (Product candidate : candidates) {
            double similarity = calculateProductSimilarity(candidate, brand, model, cleanedTitle);

            if (similarity > SIMILARITY_THRESHOLD && similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = candidate;
            }
        }

        // Either add as variant to existing product or create new product
        if (bestMatch != null) {
            addVariantToProduct(bestMatch, rawItem, color, storageInfo);
            rawItem.setMatchedProductId(bestMatch.getId());
        } else {
            Product newProduct = createNewProduct(rawItem, cleanedTitle, brand, model, categoryCode);
            addVariantToProduct(newProduct, rawItem, color, storageInfo);
            rawItem.setMatchedProductId(newProduct.getId());
        }

        // Mark as processed
        rawItem.setProcessed(true);
        //crawlerRawRepository.save(rawItem);
    }

    /**
     * Find potential matching products based on brand and model
     */
    private List<Product> findCandidateProducts(String brand, String model, String categoryCode) {
        List<Product> candidates = new ArrayList<>();

        // First try exact brand match
        if (brand != null && !brand.isEmpty()) {
            candidates.addAll(productRepository.findByBrand(brand));
        }

        // If we have very few candidates, try category
        if (candidates.size() < 10 && categoryCode != null && !categoryCode.isEmpty()) {
            candidates.addAll(productRepository.findByCategory(new ProductCategory(categoryCode)));
        }

        // If we still have very few, try a broader search using model
        if (candidates.size() < 5 && model != null && !model.isEmpty()) {
            candidates.addAll(productRepository.searchProducts(model));
        }

        return candidates;
    }

    /**
     * Calculate similarity between a product and extracted attributes
     */
    private double calculateProductSimilarity(Product product, String brand, String model, String cleanedTitle) {
        double score = 0;
        double totalWeight = 0;

        // Brand similarity (high weight)
        if (brand != null && product.getBrand() != null) {
            double brandWeight = 0.4;
            double brandSimilarity = brand.equalsIgnoreCase(product.getBrand()) ? 1.0 : 0.0;
            score += brandSimilarity * brandWeight;
            totalWeight += brandWeight;
        }

        // Model similarity (highest weight)
        if (model != null && product.getModel() != null) {
            double modelWeight = 0.5;
            double modelSimilarity;

            // Exact model match is best
            if (model.equalsIgnoreCase(product.getModel())) {
                modelSimilarity = 1.0;
            } else {
                // Otherwise use text similarity
                modelSimilarity = textProcessor.calculateTitleSimilarity(model, product.getModel());
            }

            score += modelSimilarity * modelWeight;
            totalWeight += modelWeight;
        }

        // Title similarity (lower weight, fallback)
        if (cleanedTitle != null && product.getName() != null) {
            double titleWeight = 0.3;
            double titleSimilarity = textProcessor.calculateTitleSimilarity(cleanedTitle, product.getName());
            score += titleSimilarity * titleWeight;
            totalWeight += titleWeight;
        }

        // Normalize score
        return totalWeight > 0 ? score / totalWeight : 0;
    }

    /**
     * Create a new product from a raw item
     */
    private Product createNewProduct(CrawlerRaw rawItem, String cleanedTitle, String brand, String model, String categoryCode) {
        Product product = new Product();
        product.setName(cleanedTitle);
        product.setBrand(brand);
        product.setModel(model);
        // Try to find a matching product category
        Optional<ProductCategory> categoryOpt = productCategoryRepository.findById(categoryCode);
        categoryOpt.ifPresent(product::setCategory);

        // We don't have much other data yet, but could extract more in the future

        return productRepository.save(product);
    }

    /**
     * Add a variant to an existing product
     */
    private void addVariantToProduct(Product product, CrawlerRaw rawItem, String color, String storageInfo) {
        // Get the website from the crawler job
        CrawlerWebsite website = rawItem.getJob().getCrawlerWebsite();
        String sourceUrl = rawItem.getLink();

        Optional<ProductVariant> existingVariant = productVariantRepository
                .findByProductAndWebsiteAndSourceUrl(product, website, sourceUrl);

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

            variant.setPrice(rawItem.getPrice());
            variant.setOldPrice(rawItem.getOldPrice());
            variant.setDiscount(rawItem.getDiscount());
            variant.setPriceString(rawItem.getPriceString());
            variant.setRawProductId(rawItem.getId());

            productVariantRepository.save(variant);
        }
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
        for (CrawlerRaw rawItem : unprocessedItems) {
            try {
                processRawProduct(rawItem);
                processed++;
            } catch (Exception e) {
                logger.error(
                        "Error processing raw product with id {}: {}",
                        rawItem.getId(),
                        e.getMessage(),
                        e);
            }
        }

        logger.info("Completed processing {} raw products for category {}", processed, category);
    }
}
