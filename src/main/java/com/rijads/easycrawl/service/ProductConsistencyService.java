package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.*;
import com.rijads.easycrawl.repository.*;
import com.rijads.easycrawl.utility.ProductSimilarityUtil;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for maintaining consistency in product data.
 * Performs regular checks and updates on products based on current registry state.
 */
@Service
public class ProductConsistencyService {
    private static final Logger logger = LoggerFactory.getLogger(ProductConsistencyService.class);
    private static final int BATCH_SIZE = 100;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductTextProcessor textProcessor;

    @Autowired
    private ProductRegistryRepository productRegistryRepository;

    @Autowired
    private CrawlerRawRepository crawlerRawRepository;

    @Autowired
    private ProductSimilarityUtil similarityUtil;

    /**
     * Scheduled job to verify and update product consistency
     */
    @Scheduled(cron = "0 45 0 * * ?")
    @Transactional
    public void scheduledProductConsistencyCheck() {
        logger.info("Starting scheduled product consistency check");
        performProductConsistencyCheck();
    }

    /**
     * Perform a complete product consistency check and update
     */
    @Transactional
    public Map<String, Object> performProductConsistencyCheck() {
        Map<String, Object> results = new HashMap<>();

        // Ensure the registry and similarity utilities are up to date
        textProcessor.refreshRegistry();

        // Step 1: Update products with corrected brand/model information
        ProductUpdateStats brandModelUpdates = updateProductBrandAndModel();
        results.put("brandModelUpdates", brandModelUpdates);

        // Step 2: Identify and merge similar products
        MergeStats mergeStats = identifyAndMergeSimilarProducts();
        results.put("mergeStats", mergeStats);

        // Step 3: Update product categories based on current product info
        int categoryUpdates = updateProductCategories();
        results.put("categoryUpdates", categoryUpdates);

        // Step 4: Normalize product names
        int nameNormalizations = normalizeProductNames();
        results.put("nameNormalizations", nameNormalizations);

        logger.info("Product consistency check completed. Updated brands/models: {}, Merged products: {}, Updated categories: {}, Normalized names: {}",
                brandModelUpdates.getUpdatedCount(), mergeStats.getMergedCount(), categoryUpdates, nameNormalizations);

        return results;
    }

    /**
     * Update product brand and model information based on current registry state
     */
    @Transactional
    public ProductUpdateStats updateProductBrandAndModel() {
        logger.info("Starting brand and model information update for products");

        ProductUpdateStats stats = new ProductUpdateStats();
        int page = 0;
        boolean hasMore = true;

        // Process all products in batches
        while (hasMore) {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            Page<Product> productPage = productRepository.findAll(pageable);

            if (productPage.isEmpty()) {
                hasMore = false;
                continue;
            }

            List<Product> productsToUpdate = new ArrayList<>();

            for (Product product : productPage.getContent()) {
                // Skip products without name
                if (product.getName() == null || product.getName().isEmpty()) {
                    continue;
                }

                // Re-extract brand with current registry state
                String originalBrand = product.getBrand();
                String newBrand = textProcessor.extractBrand(product.getName());

                // Re-extract model with current logic
                String originalModel = product.getModel();
                String newModel = textProcessor.extractModel(product.getName(), newBrand);

                boolean updated = false;

                // Check if brand needs updating
                if (newBrand != null && !newBrand.equals(originalBrand)) {
                    product.setBrand(newBrand);
                    stats.addBrandUpdate(product.getId(), originalBrand, newBrand);
                    updated = true;
                }

                // Check if model needs updating
                if (newModel != null && !newModel.equals(originalModel)) {
                    product.setModel(newModel);
                    stats.addModelUpdate(product.getId(), originalModel, newModel);
                    updated = true;
                }

                if (updated) {
                    productsToUpdate.add(product);
                }
            }

            // Update all modified products
            if (!productsToUpdate.isEmpty()) {
                productRepository.saveAll(productsToUpdate);
                logger.info("Updated brand/model information for {} products in batch {}",
                        productsToUpdate.size(), page);
            }

            page++;
            hasMore = productPage.hasNext();
        }

        logger.info("Completed brand and model updates. Total updates: {}", stats.getUpdatedCount());
        return stats;
    }

    /**
     * Identify and merge similar products to maintain consistency
     * Uses the shared similarity utility for consistent logic
     */
    @Transactional
    public MergeStats identifyAndMergeSimilarProducts() {
        logger.info("Starting identification of similar products for merging");

        MergeStats stats = new MergeStats();

        // Step 1: Identify products by brand
        Map<String, List<Product>> productsByBrand = new HashMap<>();

        // Only consider brands that have multiple products
        List<Object[]> brandsWithMultipleProducts = productRepository.findBrandsWithMultipleProducts();

        for (Object[] brandInfo : brandsWithMultipleProducts) {
            String brand = (String) brandInfo[0];
            Long count = (Long) brandInfo[1];

            // Only process brands with multiple products
            if (brand != null && count > 1) {
                List<Product> products = productRepository.findByBrand(brand);
                productsByBrand.put(brand, products);
            }
        }

        // Step 2: For each brand, identify similar products
        for (Map.Entry<String, List<Product>> entry : productsByBrand.entrySet()) {
            String brand = entry.getKey();
            List<Product> products = entry.getValue();

            logger.info("Analyzing {} products for brand: {}", products.size(), brand);

            // Skip if too few products
            if (products.size() < 2) {
                continue;
            }

            // Find products that should be merged
            List<List<Product>> mergeGroups = findProductsToMerge(products);

            // Perform merges
            for (List<Product> group : mergeGroups) {
                if (group.size() < 2) continue;

                // Sort by number of variants (descending)
                group.sort((p1, p2) -> {
                    int variants1 = p1.getVariants() != null ? p1.getVariants().size() : 0;
                    int variants2 = p2.getVariants() != null ? p2.getVariants().size() : 0;
                    return Integer.compare(variants2, variants1); // Descending
                });

                // Use the product with most variants as primary
                Product primaryProduct = group.get(0);
                List<Product> productsToMerge = group.subList(1, group.size());

                try {
                    mergeProducts(primaryProduct, productsToMerge);
                    stats.addMergedGroup(primaryProduct, productsToMerge);
                } catch (Exception e) {
                    logger.error("Error merging products with primary {}: {}",
                            primaryProduct.getId(), e.getMessage(), e);
                    stats.addError(primaryProduct.getId(), e.getMessage());
                }
            }
        }

        logger.info("Completed product merging. Merged {} products into {} primary products.",
                stats.getTotalMergedCount(), stats.getMergedGroupCount());

        return stats;
    }

    /**
     * Find groups of similar products that should be merged
     * Uses the shared similarity utility for consistent logic
     */
    private List<List<Product>> findProductsToMerge(List<Product> products) {
        List<List<Product>> mergeGroups = new ArrayList<>();
        Set<Integer> processedIds = new HashSet<>();

        for (int i = 0; i < products.size(); i++) {
            Product product1 = products.get(i);

            // Skip already processed products
            if (processedIds.contains(product1.getId())) {
                continue;
            }

            List<Product> similarProducts = new ArrayList<>();
            similarProducts.add(product1);
            processedIds.add(product1.getId());

            // Find similar products
            for (int j = i + 1; j < products.size(); j++) {
                Product product2 = products.get(j);

                // Skip already processed products
                if (processedIds.contains(product2.getId())) {
                    continue;
                }

                // Calculate similarity between products using the shared utility
                double similarity = similarityUtil.calculateProductToProductSimilarity(product1, product2);

                if (similarity >= ProductSimilarityUtil.MERGE_SIMILARITY_THRESHOLD) {
                    similarProducts.add(product2);
                    processedIds.add(product2.getId());
                }
            }

            // If we found similar products, add to merge groups
            if (similarProducts.size() > 1) {
                mergeGroups.add(similarProducts);
            }
        }

        return mergeGroups;
    }

    /**
     * Merge similar products, moving all variants to the primary product
     */
    @Transactional
    public void mergeProducts(Product primaryProduct, List<Product> productsToMerge) {
        for (Product product : productsToMerge) {
            // Get all variants for the product
            List<ProductVariant> variants = productVariantRepository.findByProduct(product);

            // Update each variant to point to the primary product
            for (ProductVariant variant : variants) {
                variant.setProduct(primaryProduct);
            }

            // Save all updated variants
            productVariantRepository.saveAll(variants);

            // Update raw items to point to primary product
            crawlerRawRepository.updateMatchedProductId(product.getId(), primaryProduct.getId());

            // Log the merge
            logger.info("Merged product {} ({}) into primary product {} ({})",
                    product.getId(), product.getName(),
                    primaryProduct.getId(), primaryProduct.getName());
        }

        // Delete the merged products
        for (Product product : productsToMerge) {
            productRepository.delete(product);
        }
    }

    /**
     * Update product categories based on current product information
     */
    @Transactional
    public int updateProductCategories() {
        logger.info("Starting product category updates");

        int updated = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            Page<Product> productPage = productRepository.findAll(pageable);

            if (productPage.isEmpty()) {
                hasMore = false;
                continue;
            }

            List<Product> productsToUpdate = new ArrayList<>();

            for (Product product : productPage.getContent()) {
                // Check if we can derive a better category for this product
                Optional<ProductCategory> suggestedCategory = suggestCategoryForProduct(product);

                if (suggestedCategory.isPresent() &&
                        (product.getCategory() == null ||
                                !product.getCategory().getCode().equals(suggestedCategory.get().getCode()))) {

                    // Update category
                    product.setCategory(suggestedCategory.get());
                    productsToUpdate.add(product);
                    updated++;
                }
            }

            // Save updated products
            if (!productsToUpdate.isEmpty()) {
                productRepository.saveAll(productsToUpdate);
                logger.info("Updated categories for {} products in batch {}",
                        productsToUpdate.size(), page);
            }

            page++;
            hasMore = productPage.hasNext();
        }

        logger.info("Completed category updates. Total updates: {}", updated);
        return updated;
    }

    /**
     * Suggest an appropriate category for a product based on its metadata and variants
     */
    private Optional<ProductCategory> suggestCategoryForProduct(Product product) {
        // If product already has a valid category, keep it
        if (product.getCategory() != null) {
            return Optional.of(product.getCategory());
        }

        // Check variants for category information
        List<ProductVariant> variants = productVariantRepository.findByProduct(product);

        if (!variants.isEmpty()) {
            // Look for patterns in variant titles that suggest categories
            Map<String, Integer> potentialCategories = new HashMap<>();

            // Check each variant title for category keywords
            for (ProductVariant variant : variants) {
                if (variant.getTitle() != null) {
                    // Check config code if available
                    if (variant.getSourceUrl() != null) {
                        String configCode = variant.getSourceUrl();
                        if (configCode.contains("/")) {
                            String[] parts = configCode.split("/");
                            if (parts.length > 1) {
                                String category = parts[parts.length - 1];
                                potentialCategories.put(category,
                                        potentialCategories.getOrDefault(category, 0) + 1);
                            }
                        }
                    }
                }
            }

            // Find most frequent category
            String mostFrequentCategory = null;
            int maxFrequency = 0;

            for (Map.Entry<String, Integer> entry : potentialCategories.entrySet()) {
                if (entry.getValue() > maxFrequency) {
                    maxFrequency = entry.getValue();
                    mostFrequentCategory = entry.getKey();
                }
            }

            // Look up category by code
            if (mostFrequentCategory != null) {
                Optional<ProductCategory> category = productCategoryRepository.findById(mostFrequentCategory);
                if (category.isPresent()) {
                    return category;
                }
            }
        }

        // No category could be determined
        return Optional.empty();
    }

    /**
     * Normalize product names based on brand, model, and other extracted information
     */
    @Transactional
    public int normalizeProductNames() {
        logger.info("Starting product name normalization");

        int updated = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            Page<Product> productPage = productRepository.findAll(pageable);

            if (productPage.isEmpty()) {
                hasMore = false;
                continue;
            }

            List<Product> productsToUpdate = new ArrayList<>();

            for (Product product : productPage.getContent()) {
                // Skip products without brand or model
                if (product.getBrand() == null || product.getModel() == null) {
                    continue;
                }

                // Generate a normalized name
                String normalizedName = generateNormalizedName(product);

                // Update if different
                if (!normalizedName.equals(product.getName())) {
                    product.setName(normalizedName);
                    productsToUpdate.add(product);
                    updated++;
                }
            }

            // Save updated products
            if (!productsToUpdate.isEmpty()) {
                productRepository.saveAll(productsToUpdate);
                logger.info("Normalized names for {} products in batch {}",
                        productsToUpdate.size(), page);
            }

            page++;
            hasMore = productPage.hasNext();
        }

        logger.info("Completed name normalization. Total updates: {}", updated);
        return updated;
    }

    /**
     * Generate a normalized name for a product based on its metadata
     */
    private String generateNormalizedName(Product product) {
        StringBuilder nameBuilder = new StringBuilder();

        // Start with brand
        nameBuilder.append(product.getBrand());
        nameBuilder.append(" ");

        // Add model
        nameBuilder.append(product.getModel());

        // If category is smartphone, we might want to add common attributes
        if (product.getCategory() != null &&
                "smartphones".equalsIgnoreCase(product.getCategory().getCode())) {

            // Find most common attributes from variants
            List<ProductVariant> variants = productVariantRepository.findByProduct(product);

            if (!variants.isEmpty()) {
                // Find most common storage
                Map<String, Integer> storageFrequency = new HashMap<>();
                Map<String, Integer> ramFrequency = new HashMap<>();

                for (ProductVariant variant : variants) {
                    // Add storage info
                    if (variant.getSize() != null && !variant.getSize().isEmpty()) {
                        storageFrequency.put(variant.getSize(),
                                storageFrequency.getOrDefault(variant.getSize(), 0) + 1);
                    }

                    // Add RAM info
                    if (variant.getProperty1() != null && !variant.getProperty1().isEmpty()) {
                        ramFrequency.put(variant.getProperty1(),
                                ramFrequency.getOrDefault(variant.getProperty1(), 0) + 1);
                    }
                }

                // We don't typically include color in product name, but RAM and storage are common
                String commonRam = getMostCommonAttribute(ramFrequency);
                String commonStorage = getMostCommonAttribute(storageFrequency);

                // Add RAM and storage in common format (e.g., "6+128GB")
                if (commonRam != null && commonStorage != null) {
                    String ramValue = commonRam.replaceAll("[^0-9]", "");
                    String storageValue = commonStorage.replaceAll("[^0-9]", "");

                    if (!ramValue.isEmpty() && !storageValue.isEmpty()) {
                        nameBuilder.append(" ").append(ramValue).append("+").append(storageValue).append("GB");
                    }
                }
            }
        }

        return nameBuilder.toString();
    }

    /**
     * Get the most common attribute from a frequency map
     */
    private String getMostCommonAttribute(Map<String, Integer> frequencyMap) {
        if (frequencyMap.isEmpty()) {
            return null;
        }

        return frequencyMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Class to track product updates
     */
    public static class ProductUpdateStats {
        private int totalUpdated = 0;
        private Map<Integer, String[]> brandUpdates = new HashMap<>();
        private Map<Integer, String[]> modelUpdates = new HashMap<>();

        public void addBrandUpdate(Integer productId, String oldBrand, String newBrand) {
            brandUpdates.put(productId, new String[]{oldBrand, newBrand});
            totalUpdated++;
        }

        public void addModelUpdate(Integer productId, String oldModel, String newModel) {
            modelUpdates.put(productId, new String[]{oldModel, newModel});
            totalUpdated++;
        }

        public int getUpdatedCount() {
            return totalUpdated;
        }

        public Map<Integer, String[]> getBrandUpdates() {
            return brandUpdates;
        }

        public Map<Integer, String[]> getModelUpdates() {
            return modelUpdates;
        }
    }

    /**
     * Class to track product merges
     */
    public static class MergeStats {
        private List<MergeGroup> mergedGroups = new ArrayList<>();
        private Map<Integer, String> errors = new HashMap<>();

        public void addMergedGroup(Product primaryProduct, List<Product> mergedProducts) {
            mergedGroups.add(new MergeGroup(primaryProduct, mergedProducts));
        }

        public void addError(Integer productId, String errorMessage) {
            errors.put(productId, errorMessage);
        }

        public int getMergedGroupCount() {
            return mergedGroups.size();
        }

        public int getTotalMergedCount() {
            return mergedGroups.stream()
                    .mapToInt(group -> group.mergedProducts.size())
                    .sum();
        }

        public int getMergedCount() {
            return getTotalMergedCount();
        }

        public List<MergeGroup> getMergedGroups() {
            return mergedGroups;
        }

        public Map<Integer, String> getErrors() {
            return errors;
        }

        public static class MergeGroup {
            private final Product primaryProduct;
            private final List<Product> mergedProducts;

            public MergeGroup(Product primaryProduct, List<Product> mergedProducts) {
                this.primaryProduct = primaryProduct;
                this.mergedProducts = new ArrayList<>(mergedProducts);
            }

            public Product getPrimaryProduct() {
                return primaryProduct;
            }

            public List<Product> getMergedProducts() {
                return mergedProducts;
            }
        }
    }
}