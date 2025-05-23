package com.rijads.easycrawl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rijads.easycrawl.model.*;
import com.rijads.easycrawl.repository.*;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final CrawlerRawRepository crawlerRawRepository;
    private final ProductRepository productRepository;
    private final ProductRegistryRepository productRegistryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductTextProcessor textProcessor;
    private final ProductCategoryRepository productCategoryRepository;
    private final UnmappableItemRepository unmappableItemRepository;
    private final JobRepository jobRepository;
    private final JobErrorRepository jobErrorRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public ProductMatchingService(
            CrawlerRawRepository crawlerRawRepository,
            ProductRepository productRepository,
            ProductRegistryRepository productRegistryRepository,
            ProductVariantRepository productVariantRepository,
            ProductTextProcessor textProcessor,
            ProductCategoryRepository productCategoryRepository,
            UnmappableItemRepository unmappableItemRepository,
            JobRepository jobRepository,
            JobErrorRepository jobErrorRepository,
            PriceHistoryRepository priceHistoryRepository) {
        this.crawlerRawRepository = crawlerRawRepository;
        this.productRepository = productRepository;
        this.productRegistryRepository = productRegistryRepository;
        this.productVariantRepository = productVariantRepository;
        this.textProcessor = textProcessor;
        this.productCategoryRepository = productCategoryRepository;
        this.unmappableItemRepository = unmappableItemRepository;
        this.jobRepository = jobRepository;
        this.jobErrorRepository = jobErrorRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

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

        // Get ALL unprocessed items without grouping by title
        long startTime = System.currentTimeMillis();
        if (categoryCode != null) {
            unprocessedItems = crawlerRawRepository.findByProcessedNullOrFalseAndConfigCodeContaining(categoryCode);
            logger.info("Found {} unprocessed raw products for category {}", 
                    unprocessedItems.size(), categoryCode);
        } else {
            unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();
            logger.info("Found {} unprocessed raw products", unprocessedItems.size());
        }

        int processed = 0;
        int skipped = 0;
        int pricesRecorded = 0;
        int newVariantsCreated = 0;
        int variantsUpdated = 0;
        int newProductsCreated = 0;
        int totalItems = unprocessedItems.size();
        
        // For performance, group items by LINK, not title
        // This is more accurate as different links usually represent different product variants
        Map<String, List<CrawlerRaw>> itemsByLink = unprocessedItems.stream()
                .filter(item -> item.getLink() != null && !item.getLink().isEmpty())
                .collect(Collectors.groupingBy(CrawlerRaw::getLink));
        
        // Create a separate group for items with null/empty links (rare but possible)
        List<CrawlerRaw> itemsWithEmptyLinks = unprocessedItems.stream()
                .filter(item -> item.getLink() == null || item.getLink().isEmpty())
                .collect(Collectors.toList());
                
        int uniqueLinkCount = itemsByLink.size() + (itemsWithEmptyLinks.isEmpty() ? 0 : 1);
        
        logger.info("PROGRESS: [0%] Starting to process {} unique product links from {} total items", 
                uniqueLinkCount, totalItems);

        // Process items grouped by link
        int linksProcessed = 0;
        int lastProgressUpdate = 0;
        
        // First process items with valid links
        for (Map.Entry<String, List<CrawlerRaw>> entry : itemsByLink.entrySet()) {
            String link = entry.getKey();
            List<CrawlerRaw> itemsWithSameLink = entry.getValue();
            
            try {
                // For each link, sort items by creation date (oldest first to maintain natural order)
                itemsWithSameLink.sort(Comparator.comparing(CrawlerRaw::getCreated, 
                        Comparator.nullsLast(Comparator.naturalOrder())));
                
                // Process the first/oldest item to establish product mapping
                CrawlerRaw firstItem = itemsWithSameLink.get(0);
                boolean mapped = processRawProduct(firstItem);
                
                if (mapped && firstItem.getMatchedProductId() != null) {
                    // Check if this is a newly created product
                    if (firstItem.getMatchedProductId() > 0 && 
                            !productRepository.existsByIdAndCreatedBefore(
                                firstItem.getMatchedProductId(), 
                                LocalDateTime.now().minusMinutes(5))) {
                        newProductsCreated++;
                    }
                    
                    // Now record price history for ALL items with this link
                    // This is critical to maintain complete price history!
                    for (int i = 0; i < itemsWithSameLink.size(); i++) {
                        CrawlerRaw item = itemsWithSameLink.get(i);
                        // Mark all items as processed and map to same product
                        item.setProcessed(true);
                        item.setMatchedProductId(firstItem.getMatchedProductId());
                        crawlerRawRepository.save(item);
                        
                        // For the first item, price is already recorded during processRawProduct
                        // For others, we need to explicitly record price history by date
                        if (i > 0) {
                            // Find the variant already created by the first item
                            Product product = new Product();
                            product.setId(firstItem.getMatchedProductId());
                            Optional<ProductVariant> variant = productVariantRepository
                                    .findByProductAndSourceUrl(
                                            product, 
                                            link);
                            
                            if (variant.isPresent()) {
                                // Just record price history for this item's date
                                // No need to update the variant itself again
                                recordPriceHistoryForRawItem(variant.get(), item);
                                pricesRecorded++;
                            } else {
                                // This is unusual - the variant should have been created
                                // by the first item, but just in case, create it
                                PriceProcessingResult result = processItemForPrice(item, firstItem.getMatchedProductId());
                                if (result.priceRecorded) pricesRecorded++;
                                if (result.newVariantCreated) newVariantsCreated++;
                                if (result.variantUpdated) variantsUpdated++;
                            }
                        } else {
                            // Count the price recorded by the first item
                            pricesRecorded++;
                        }
                    }
                    
                    processed += itemsWithSameLink.size();
                } else {
                    // First item wasn't mappable, so we'll skip all others with same link
                    skipped += itemsWithSameLink.size();
                }

                linksProcessed++;
                
                // Calculate progress percentage
                int progressPercentage = (int)((processed + skipped) * 100.0 / totalItems);
                int linksProgressPercentage = (int)((double)linksProcessed * 100.0 / uniqueLinkCount);
                
                // Log progress at regular intervals or when percentage changes significantly
                // Now we'll update more frequently
                if (progressPercentage != lastProgressUpdate || linksProcessed % 20 == 0) {
                    lastProgressUpdate = progressPercentage;
                    long elapsed = System.currentTimeMillis() - startTime;
                    
                    // Estimate remaining time
                    long timePerItem = (processed + skipped) > 0 ? elapsed / (processed + skipped) : 0;
                    long estimatedTotalTime = timePerItem * totalItems;
                    long remainingTime = estimatedTotalTime - elapsed;
                    
                    String progressBar = createProgressBar(progressPercentage);
                    
                    logger.info(
                            "PROGRESS: {}% {} | Links: {}% ({}/{}) | Items: {}/{} | Products: {} | Variants: +{}/±{} | Prices: {} | Est: ~{}min",
                            progressPercentage,
                            progressBar,
                            linksProgressPercentage,
                            linksProcessed,
                            uniqueLinkCount,
                            processed + skipped,
                            totalItems,
                            newProductsCreated,
                            newVariantsCreated,
                            variantsUpdated,
                            pricesRecorded,
                            remainingTime / 60000);
                    
                    // Update job description with progress so it's visible in the UI
                    if (job != null) {
                        job.setDescription(String.format(
                            "Processing %d items: %d%% complete | %d processed, %d skipped | %d new products, %d new variants",
                            totalItems, progressPercentage, processed, skipped, newProductsCreated, newVariantsCreated));
                        jobRepository.save(job);
                    }
                }
            } catch (Exception e) {
                logger.error(
                        "Error processing raw products with link {}: {}",
                        link,
                        e.getMessage(),
                        e);

                // Record error in job_error table for the first item in the group
                CrawlerRaw firstItem = itemsWithSameLink.get(0);
                String source = firstItem.getConfigCode().split("/")[0];
                String category = extractCategory(firstItem.getConfigCode());
                createJobError(job, source, category, e);
                
                skipped += itemsWithSameLink.size();
            }
        }
        
        // Now process items with empty links (if any)
        if (!itemsWithEmptyLinks.isEmpty()) {
            logger.info("PROGRESS: [{}%] Processing {} items with empty/missing links", 
                    (int)((processed + skipped) * 100.0 / totalItems),
                    itemsWithEmptyLinks.size());
            
            // Group by title as fallback for items with no links
            Map<String, List<CrawlerRaw>> emptyLinkItemsByTitle = itemsWithEmptyLinks.stream()
                    .collect(Collectors.groupingBy(CrawlerRaw::getTitle));
                    
            for (Map.Entry<String, List<CrawlerRaw>> entry : emptyLinkItemsByTitle.entrySet()) {
                String title = entry.getKey();
                List<CrawlerRaw> itemsWithSameTitle = entry.getValue();
                
                try {
                    // Process the first item to establish product mapping
                    CrawlerRaw firstItem = itemsWithSameTitle.get(0);
                    boolean mapped = processRawProduct(firstItem);
                    
                    if (mapped && firstItem.getMatchedProductId() != null) {
                        // Check if this is a newly created product
                        if (firstItem.getMatchedProductId() > 0 && 
                                !productRepository.existsByIdAndCreatedBefore(
                                    firstItem.getMatchedProductId(), 
                                    LocalDateTime.now().minusMinutes(5))) {
                            newProductsCreated++;
                        }
                        
                        // Process all other items
                        for (int i = 1; i < itemsWithSameTitle.size(); i++) {
                            CrawlerRaw otherItem = itemsWithSameTitle.get(i);
                            otherItem.setProcessed(true);
                            otherItem.setMatchedProductId(firstItem.getMatchedProductId());
                            crawlerRawRepository.save(otherItem);
                            
                            // Process for price history
                            PriceProcessingResult result = processItemForPrice(otherItem, firstItem.getMatchedProductId());
                            if (result.priceRecorded) pricesRecorded++;
                            if (result.newVariantCreated) newVariantsCreated++;
                            if (result.variantUpdated) variantsUpdated++;
                        }
                        
                        processed += itemsWithSameTitle.size();
                    } else {
                        skipped += itemsWithSameTitle.size();
                    }
                    
                    // Calculate progress percentage
                    int progressPercentage = (int)((processed + skipped) * 100.0 / totalItems);
                    if (progressPercentage != lastProgressUpdate) {
                        lastProgressUpdate = progressPercentage;
                        String progressBar = createProgressBar(progressPercentage);
                        logger.info(
                                "PROGRESS: {}% {} | Items: {}/{} | Products: {} | Variants: +{}/±{} | Prices: {}",
                                progressPercentage,
                                progressBar,
                                processed + skipped,
                                totalItems,
                                newProductsCreated,
                                newVariantsCreated,
                                variantsUpdated,
                                pricesRecorded);
                    }
                } catch (Exception e) {
                    logger.error("Error processing items with title {}: {}", title, e.getMessage());
                    skipped += itemsWithSameTitle.size();
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        String finalProgressBar = createProgressBar(100);
        logger.info(
                "PROGRESS: 100% {} | DONE! Processed {} items ({} skipped) in {}min | Products: {} | Variants: +{}/±{} | Prices: {}",
                finalProgressBar,
                processed,
                skipped, 
                totalTime / 60000,
                newProductsCreated,
                newVariantsCreated,
                variantsUpdated,
                pricesRecorded);
        
        if (processed > 0) {
            logger.info("Average processing speed: {} items/sec", 
                    String.format("%.2f", processed / (totalTime / 1000.0)));
        }
    }
    
    /**
     * Create a text-based progress bar
     */
    private String createProgressBar(int percentage) {
        int barLength = 20; // Length of the progress bar
        int completedLength = (int)Math.round(percentage / (100.0 / barLength));
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < completedLength) {
                sb.append("=");
            } else if (i == completedLength) {
                sb.append(">");
            } else {
                sb.append(" ");
            }
        }
        sb.append("]");
        
        return sb.toString();
    }

    /**
     * Record price history for a raw item using an existing variant
     * This is used when we want to record price history without updating the variant itself
     */
    private void recordPriceHistoryForRawItem(ProductVariant variant, CrawlerRaw rawItem) {
        // Use the crawl timestamp from the raw item
        LocalDateTime recordTime = rawItem.getCreated();
        if (recordTime == null) {
            recordTime = LocalDateTime.now();
        }

        LocalDateTime startOfDay = recordTime.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        
        // Check if there's already a price history entry for the day this item was crawled
        List<PriceHistory> sameDayEntries = priceHistoryRepository
                .findByVariantAndRecordedAtBetweenOrderByRecordedAtDesc(
                        variant, startOfDay, endOfDay);
        
        if (!sameDayEntries.isEmpty()) {
            // Only add a new entry if the price changed from the previous one
            PriceHistory existingEntry = sameDayEntries.get(0);
            
            // Only update if the price information has changed
            boolean priceInfoChanged = 
                    !Objects.equals(existingEntry.getPrice(), rawItem.getPrice()) ||
                    !Objects.equals(existingEntry.getOldPrice(), rawItem.getOldPrice()) ||
                    !Objects.equals(existingEntry.getDiscount(), rawItem.getDiscount()) ||
                    !Objects.equals(existingEntry.getPriceString(), rawItem.getPriceString());
            
            if (priceInfoChanged) {
                // Create a new entry with the raw item's price and timestamp
                PriceHistory history = new PriceHistory();
                history.setVariant(variant);
                history.setWebsite(variant.getWebsite());
                history.setPrice(rawItem.getPrice());
                history.setOldPrice(rawItem.getOldPrice());
                history.setDiscount(rawItem.getDiscount());
                history.setPriceString(rawItem.getPriceString());
                history.setRecordedAt(recordTime);
                
                priceHistoryRepository.save(history);
            }
        } else {
            // Create a new entry for this day
            PriceHistory history = new PriceHistory();
            history.setVariant(variant);
            history.setWebsite(variant.getWebsite());
            history.setPrice(rawItem.getPrice());
            history.setOldPrice(rawItem.getOldPrice());
            history.setDiscount(rawItem.getDiscount());
            history.setPriceString(rawItem.getPriceString());
            history.setRecordedAt(recordTime);
            
            priceHistoryRepository.save(history);
        }
    }

    /**
     * Simple class to track price processing results
     */
    private static class PriceProcessingResult {
        boolean priceRecorded = false;
        boolean newVariantCreated = false;
        boolean variantUpdated = false;
    }

    /**
     * Process an item specifically for its price data
     * This is a lightweight version of processRawProduct that only updates price info
     */
    private PriceProcessingResult processItemForPrice(CrawlerRaw rawItem, Integer productId) {
        PriceProcessingResult result = new PriceProcessingResult();
        try {
            // Find the product
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                logger.warn("Could not find product with ID {} for price processing", productId);
                return result;
            }
            
            Product product = productOpt.get();
            CrawlerWebsite website = rawItem.getJob().getCrawlerWebsite();
            String sourceUrl = rawItem.getLink();
            
            // Extract attributes
            String color = textProcessor.extractColor(rawItem.getTitle());
            String storageInfo = textProcessor.extractStorageInfo(rawItem.getTitle());
            String property1 = "";
            String categoryCode = extractCategory(rawItem.getConfigCode());
            if (categoryCode.equalsIgnoreCase("smartphones")) {
                property1 = textProcessor.extractRamInfo(rawItem.getTitle());
            }
            
            // Find existing variant by URL
            Optional<ProductVariant> existingVariantByUrl =
                    productVariantRepository.findByProductAndSourceUrl(product, sourceUrl);

            if (existingVariantByUrl.isPresent()) {
                // Update existing variant
                ProductVariant variant = existingVariantByUrl.get();
                updateVariantPrice(variant, rawItem, result);
                return result;
            }
            
            // Find by attributes
            List<ProductVariant> existingVariants = productVariantRepository.findByProduct(product);
            
            for (ProductVariant existingVariant : existingVariants) {
                if (existingVariant.getWebsite().getCode().equals(website.getCode()) &&
                        Objects.equals(existingVariant.getColor(), color) &&
                        Objects.equals(existingVariant.getSize(), storageInfo) && 
                        Objects.equals(existingVariant.getProperty1(), property1)) {
                    
                    existingVariant.setSourceUrl(sourceUrl); // Update URL
                    updateVariantPrice(existingVariant, rawItem, result);
                    return result;
                }
            }
            
            // Create new variant if no match found
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setWebsite(website);
            variant.setSourceUrl(sourceUrl);
            variant.setTitle(rawItem.getTitle());
            variant.setColor(color);
            variant.setSize(storageInfo);
            variant.setProperty1(property1);
            variant.setPrice(rawItem.getPrice());
            variant.setOldPrice(rawItem.getOldPrice());
            variant.setDiscount(rawItem.getDiscount());
            variant.setPriceString(rawItem.getPriceString());
            variant.setRawProductId(rawItem.getId());
            variant.setInStock(true);

            ProductVariant savedVariant = productVariantRepository.save(variant);
            result.newVariantCreated = true;
            
            // Always record price history for new variants
            recordPriceHistory(savedVariant, rawItem);
            result.priceRecorded = true;
            
            return result;
        } catch (Exception e) {
            logger.error("Error processing item {} for price: {}", rawItem.getId(), e.getMessage());
            return result;
        }
    }
    
    /**
     * Update variant price and record price history
     */
    private void updateVariantPrice(ProductVariant variant, CrawlerRaw rawItem, PriceProcessingResult result) {
        // Check if there's any change in price data
        boolean priceChanged = !Objects.equals(variant.getPrice(), rawItem.getPrice()) || 
                            !Objects.equals(variant.getOldPrice(), rawItem.getOldPrice()) ||
                            !Objects.equals(variant.getDiscount(), rawItem.getDiscount()) ||
                            !Objects.equals(variant.getPriceString(), rawItem.getPriceString());
                            
        // Update price data
        variant.setPrice(rawItem.getPrice());
        variant.setOldPrice(rawItem.getOldPrice());
        variant.setDiscount(rawItem.getDiscount());
        variant.setPriceString(rawItem.getPriceString());
        variant.setRawProductId(rawItem.getId());
        variant.setInStock(true);
        
        ProductVariant savedVariant = productVariantRepository.save(variant);
        result.variantUpdated = true;
        
        // Always record price history
        recordPriceHistory(savedVariant, rawItem);
        result.priceRecorded = true;
    }
    
    /**
     * Update variant price and record price history
     * Overloaded method for backward compatibility
     */
    private void updateVariantPrice(ProductVariant variant, CrawlerRaw rawItem) {
        updateVariantPrice(variant, rawItem, new PriceProcessingResult());
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

        // Get all unprocessed items without grouping
        List<CrawlerRaw> unprocessedItems = crawlerRawRepository.findByProcessedNullOrProcessedFalse();

        resultDescription.append("Found ").append(unprocessedItems.size())
                .append(" unprocessed items\n\n");

        logger.info("Found {} unprocessed raw products", unprocessedItems.size());

        int processed = 0;
        int skipped = 0;
        Map<String, Integer> categoryStats = new HashMap<>();
        Map<String, Integer> brandCounts = new HashMap<>();
        List<String> newProductNames = new ArrayList<>();
        
        // Group items by title for efficient processing
        Map<String, List<CrawlerRaw>> itemsByTitle = unprocessedItems.stream()
                .collect(Collectors.groupingBy(CrawlerRaw::getTitle));
        
        logger.info("Grouped {} raw items into {} unique titles for efficient processing", 
                unprocessedItems.size(), itemsByTitle.size());

        // Process by title groups
        for (Map.Entry<String, List<CrawlerRaw>> entry : itemsByTitle.entrySet()) {
            String title = entry.getKey();
            List<CrawlerRaw> itemsWithSameTitle = entry.getValue();
            
            try {
                // Extract category for statistics from first item
                String category = extractCategory(itemsWithSameTitle.get(0).getConfigCode());
                categoryStats.put(category, categoryStats.getOrDefault(category, 0) + itemsWithSameTitle.size());

                // Process the first item to establish product mapping
                CrawlerRaw firstItem = itemsWithSameTitle.get(0);
                boolean mapped = processRawProduct(firstItem);
                
                if (mapped && firstItem.getMatchedProductId() != null) {
                    // Record the product for statistics (using first item)
                    Optional<Product> product = productRepository.findById(firstItem.getMatchedProductId());
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
                    
                    // Process all other items with same title for their price data
                    for (int i = 1; i < itemsWithSameTitle.size(); i++) {
                        CrawlerRaw otherItem = itemsWithSameTitle.get(i);
                        // Mark as processed and map to same product
                        otherItem.setProcessed(true);
                        otherItem.setMatchedProductId(firstItem.getMatchedProductId());
                        crawlerRawRepository.save(otherItem);
                        
                        // Process the item for its price
                        processItemForPrice(otherItem, firstItem.getMatchedProductId());
                    }
                    
                    processed += itemsWithSameTitle.size();
                } else {
                    // First item wasn't mappable, so skip all with same title
                    skipped += itemsWithSameTitle.size();
                    
                    // Track the unmappable item
                    trackUnmappableItem(
                            firstItem,
                            UnmappableItem.ReasonCode.OTHER,
                            "Could not map item to product");
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
                        "Error processing raw products with title {}: {}",
                        title,
                        e.getMessage(),
                        e);

                // Track the error for the first item in the group
                CrawlerRaw firstItem = itemsWithSameTitle.get(0);
                trackUnmappableItem(
                        firstItem,
                        UnmappableItem.ReasonCode.OTHER,
                        "Error processing: " + e.getMessage());

                skipped += itemsWithSameTitle.size();
            }
        }

        // Build final results summary (same as before)
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

        // Get all unprocessed items for category without grouping
        List<CrawlerRaw> unprocessedItems = crawlerRawRepository.findByProcessedNullOrFalseAndConfigCodeContaining(category);

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
        
        // Group items by title for efficient processing
        Map<String, List<CrawlerRaw>> itemsByTitle = unprocessedItems.stream()
                .collect(Collectors.groupingBy(CrawlerRaw::getTitle));
        
        logger.info("Grouped {} raw items into {} unique titles for efficient processing", 
                unprocessedItems.size(), itemsByTitle.size());

        // Process by title groups
        for (Map.Entry<String, List<CrawlerRaw>> entry : itemsByTitle.entrySet()) {
            String title = entry.getKey();
            List<CrawlerRaw> itemsWithSameTitle = entry.getValue();
            
            try {
                // Process the first item to establish product mapping
                CrawlerRaw firstItem = itemsWithSameTitle.get(0);
                boolean mapped = processRawProduct(firstItem);
                
                if (mapped && firstItem.getMatchedProductId() != null) {
                    // Track the product for reporting
                    Optional<Product> product = productRepository.findById(firstItem.getMatchedProductId());
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
                    
                    // Process all other items with same title for their price data
                    for (int i = 1; i < itemsWithSameTitle.size(); i++) {
                        CrawlerRaw otherItem = itemsWithSameTitle.get(i);
                        // Mark as processed and map to same product
                        otherItem.setProcessed(true);
                        otherItem.setMatchedProductId(firstItem.getMatchedProductId());
                        crawlerRawRepository.save(otherItem);
                        
                        // Process the item for its price
                        processItemForPrice(otherItem, firstItem.getMatchedProductId());
                    }
                    
                    processed += itemsWithSameTitle.size();
                } else {
                    // First item wasn't mappable, so skip all with same title
                    skipped += itemsWithSameTitle.size();
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
                        "Error processing raw products with title {}: {}",
                        title,
                        e.getMessage(),
                        e);

                skipped += itemsWithSameTitle.size();
            }
        }

        // Build final results summary (same as before)
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
                "Completed processing {} raw products for category {} ({} skipped)",
                processed,
                category,
                skipped);

        // Save the final description to the job
        job.setDescription(resultDescription.toString());
        jobRepository.save(job);

        return processed;
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

        // If no brand detected, try to extract it from the title and add it to registry
        if (brand == null || brand.isEmpty()) {
            logger.info("No brand detected from registry for product: {}", rawItem.getTitle());
            
            // Try to extract potential brand from title
            String potentialBrand = extractPotentialBrandFromTitle(rawItem.getTitle());
            
            if (potentialBrand != null && !potentialBrand.isEmpty()) {
                // First check if this word is already in the registry as a non-brand type
                boolean isNonBrandType = productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                        potentialBrand, ProductRegistry.RegistryType.COLOR) ||
                        productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                        potentialBrand, ProductRegistry.RegistryType.COMMON_WORD) ||
                        productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                        potentialBrand, ProductRegistry.RegistryType.NOT_BRAND);
                
                if (isNonBrandType) {
                    logger.info("Potential brand '{}' is already in registry as a non-brand type, skipping", 
                            potentialBrand);
                } else {
                    // Add potential brand to registry
                    ProductRegistry registryEntry = new ProductRegistry();
                    registryEntry.setRegistryType(ProductRegistry.RegistryType.BRAND);
                    registryEntry.setRegistryKey(textProcessor.capitalizeFirstLetter(potentialBrand));
                    registryEntry.setDescription("Auto-added from title analysis");
                    registryEntry.setEnabled(true);
                    
                    try {
                        productRegistryRepository.save(registryEntry);
                        logger.info("Added new brand '{}' to registry from title", potentialBrand);
                        
                        // Refresh registry to include the new brand
                        textProcessor.refreshRegistry();
                        
                        // Try to extract brand again with the updated registry
                        brand = textProcessor.extractBrand(rawItem.getTitle());
                        logger.info("Re-extracted brand after registry update: {}", brand);
                    } catch (Exception e) {
                        logger.error("Error adding potential brand '{}' to registry: {}", 
                                potentialBrand, e.getMessage());
                    }
                }
            }
            
            // Quick check for similar mapped products (with timeout/limits)
            if (brand == null || brand.isEmpty()) {
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
                    
                    // Update all items with the same title
                    updateAllItemsWithSameTitle(rawItem.getTitle(), matchedProduct.getId());
                    
                    return true;
                } else {
                    // If we can't establish a brand, don't mark as processed
                    // This allows future processing attempts when registry is updated
                    logger.info("Unable to map item without brand. Item will remain unprocessed: {}", 
                            rawItem.getTitle());
                    return false;
                }
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
            // Update all items with the same title
            updateAllItemsWithSameTitle(rawItem.getTitle(), bestMatch.getId());
        } else {
            // Before creating a new product, check for exact brand and model match
            // This helps prevent duplicates created during the same processing batch
            Product existingProduct = findExactBrandModelMatch(brand, model);
            
            if (existingProduct != null) {
                // Use the existing product instead of creating a new one
                logger.info("Found existing product with exact brand/model match: {} {}", brand, model);
                addVariantToProduct(existingProduct, rawItem, color, storageInfo, property1);
                updateAllItemsWithSameTitle(rawItem.getTitle(), existingProduct.getId());
            } else {
                // No duplicate found, create new product
                Product newProduct = createNewProduct(rawItem, cleanedTitle, brand, model, categoryCode);
                addVariantToProduct(newProduct, rawItem, color, storageInfo, property1);
                updateAllItemsWithSameTitle(rawItem.getTitle(), newProduct.getId());

                // Log that we created a new product
                logger.info(
                        "Created new product for '{}' with brand '{}'",
                        rawItem.getTitle(),
                        brand);
            }
        }

        return true;
    }

    /**
     * Find a product with exact brand and model match
     * This is a stricter check than similarity matching to prevent duplicates
     */
    private Product findExactBrandModelMatch(String brand, String model) {
        if (brand == null || model == null || brand.isEmpty() || model.isEmpty()) {
            return null;
        }
        
        // First try exact case-insensitive match
        List<Product> exactMatches = productRepository.findByBrandIgnoreCaseAndModelIgnoreCase(brand, model);
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(0);
        }
        
        // Try with normalized model (removing spaces, converting to lowercase)
        String normalizedModel = model.replaceAll("\\s+", "").toLowerCase();
        
        // Get products with the same brand
        List<Product> sameProducts = productRepository.findByBrandIgnoreCase(brand);
        
        // Look for normalized model match
        for (Product product : sameProducts) {
            if (product.getModel() != null) {
                String productNormalizedModel = product.getModel().replaceAll("\\s+", "").toLowerCase();
                
                // Check for exact normalized match or models that just differ by a + character
                if (normalizedModel.equals(productNormalizedModel) || 
                    normalizedModel.equals(productNormalizedModel + "+") ||
                    (normalizedModel + "+").equals(productNormalizedModel)) {
                    return product;
                }
            }
        }
        
        return null;
    }

    /**
     * Find potential matching products based on brand and model
     */
    private List<Product> findCandidateProducts(String brand, String model, String categoryCode) {
        List<Product> candidates = new ArrayList<>();

        // First try exact brand/model match to avoid duplicates
        Product exactMatch = findExactBrandModelMatch(brand, model);
        if (exactMatch != null) {
            candidates.add(exactMatch);
            return candidates;
        }

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

        // First check if we already have this exact URL
        Optional<ProductVariant> existingVariantByUrl =
                productVariantRepository.findByProductAndSourceUrl(product, sourceUrl);

        if (existingVariantByUrl.isPresent()) {
            // Update existing variant with the same URL
            ProductVariant variant = existingVariantByUrl.get();
            
            // Check if there's any change in price data before updating
            boolean priceChanged = !Objects.equals(variant.getPrice(), rawItem.getPrice()) || 
                                !Objects.equals(variant.getOldPrice(), rawItem.getOldPrice()) ||
                                !Objects.equals(variant.getDiscount(), rawItem.getDiscount()) ||
                                !Objects.equals(variant.getPriceString(), rawItem.getPriceString());
            
            if (priceChanged) {
                logger.debug("Price changed for variant {}: {} -> {}", 
                        variant.getId(), variant.getPrice(), rawItem.getPrice());
            }
            
            // Always update the variant with the latest data
            variant.setPrice(rawItem.getPrice());
            variant.setOldPrice(rawItem.getOldPrice());
            variant.setDiscount(rawItem.getDiscount());
            variant.setPriceString(rawItem.getPriceString());
            variant.setInStock(true); // Default to in-stock for fresh data
            
            ProductVariant savedVariant = productVariantRepository.save(variant);
            
            // Always record price history for each crawler run
            // The recordPriceHistory method will handle updates for the same day
            recordPriceHistory(savedVariant, rawItem);
            
            logger.debug("Updated existing variant {} from {} with price {}",
                    variant.getId(), website.getName(), rawItem.getPrice());
            
            return;
        }

        // If no exact URL match, look for a variant with the same key attributes 
        // (same website, color, storage, property1)
        // This handles cases where the URL might have changed but it's the same variant
        List<ProductVariant> existingVariants = productVariantRepository.findByProduct(product);
        
        for (ProductVariant existingVariant : existingVariants) {
            if (existingVariant.getWebsite().getCode().equals(website.getCode()) &&
                    Objects.equals(existingVariant.getColor(), color) &&
                    Objects.equals(existingVariant.getSize(), storageInfo) && 
                    Objects.equals(existingVariant.getProperty1(), property1)) {
                
                // Check if there's any change in price data
                boolean priceChanged = !Objects.equals(existingVariant.getPrice(), rawItem.getPrice()) || 
                                    !Objects.equals(existingVariant.getOldPrice(), rawItem.getOldPrice()) ||
                                    !Objects.equals(existingVariant.getDiscount(), rawItem.getDiscount()) ||
                                    !Objects.equals(existingVariant.getPriceString(), rawItem.getPriceString());
                
                if (priceChanged) {
                    logger.debug("Price changed for variant {}: {} -> {}", 
                            existingVariant.getId(), existingVariant.getPrice(), rawItem.getPrice());
                }
                
                // Always update the variant with the latest data
                existingVariant.setSourceUrl(sourceUrl); // Update with new URL
                existingVariant.setPrice(rawItem.getPrice());
                existingVariant.setOldPrice(rawItem.getOldPrice());
                existingVariant.setDiscount(rawItem.getDiscount());
                existingVariant.setPriceString(rawItem.getPriceString());
                existingVariant.setTitle(rawItem.getTitle()); // Update title in case it changed
                existingVariant.setRawProductId(rawItem.getId());
                existingVariant.setInStock(true);
                
                ProductVariant savedVariant = productVariantRepository.save(existingVariant);
                
                // Always record price history for each crawler run
                // The recordPriceHistory method will handle updates for the same day
                recordPriceHistory(savedVariant, rawItem);
                
                logger.debug("Updated matching variant {} from {} with new URL and price {}",
                        existingVariant.getId(), website.getName(), rawItem.getPrice());
                
                return;
            }
        }

        // Create new variant if no match found
        logger.debug("Creating new variant for product {} from website {}", 
                product.getId(), website.getName());
                
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
        variant.setInStock(true);

        ProductVariant savedVariant = productVariantRepository.save(variant);
        
        // Always record price history for new variants
        recordPriceHistory(savedVariant, rawItem);
        
        logger.debug("Created new variant {} for product {} from {} with price {}",
                savedVariant.getId(), product.getId(), website.getName(), rawItem.getPrice());
    }
    
    /**
     * Record price history for a variant
     * Creates one entry per day per variant, while preserving historical data
     * Uses the crawler raw item's actual creation timestamp
     */
    private void recordPriceHistory(ProductVariant variant, CrawlerRaw rawItem) {
        // Use the crawl timestamp from the raw item
        LocalDateTime recordTime = rawItem.getCreated();
        if (recordTime == null) {
            recordTime = LocalDateTime.now();
        }

        LocalDateTime startOfDay = recordTime.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        
        // Check if there's already a price history entry for the day this item was crawled
        List<PriceHistory> sameDeysEntries = priceHistoryRepository
                .findByVariantAndRecordedAtBetweenOrderByRecordedAtDesc(
                        variant, startOfDay, endOfDay);
        
        if (!sameDeysEntries.isEmpty()) {
            // Update the existing entry for that day
            PriceHistory existingEntry = sameDeysEntries.get(0);
            
            // Only update if the price information has changed
            boolean priceInfoChanged = 
                    !Objects.equals(existingEntry.getPrice(), variant.getPrice()) ||
                    !Objects.equals(existingEntry.getOldPrice(), variant.getOldPrice()) ||
                    !Objects.equals(existingEntry.getDiscount(), variant.getDiscount()) ||
                    !Objects.equals(existingEntry.getPriceString(), variant.getPriceString());
            
            if (priceInfoChanged) {
                existingEntry.setPrice(variant.getPrice());
                existingEntry.setOldPrice(variant.getOldPrice());
                existingEntry.setDiscount(variant.getDiscount());
                existingEntry.setPriceString(variant.getPriceString());
                // Update to the original crawl time
                existingEntry.setRecordedAt(recordTime);
                
                priceHistoryRepository.save(existingEntry);
            }
        } else {
            // Create a new entry for this day
            PriceHistory history = new PriceHistory();
            history.setVariant(variant);
            history.setWebsite(variant.getWebsite());
            history.setPrice(variant.getPrice());
            history.setOldPrice(variant.getOldPrice());
            history.setDiscount(variant.getDiscount());
            history.setPriceString(variant.getPriceString());
            history.setRecordedAt(recordTime);
            
            priceHistoryRepository.save(history);
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
            boolean existsAsBrand = productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.BRAND);

            if (existsAsBrand) {
                logger.info("Brand '{}' already exists in registry", normalizedBrand);
                continue;
            }
            
            // Check if this word is already in the registry as a non-brand type
            boolean isNonBrandType = productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.COLOR) ||
                    productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.COMMON_WORD) ||
                    productRegistryRepository.existsByRegistryKeyIgnoreCaseAndRegistryType(
                    normalizedBrand, ProductRegistry.RegistryType.NOT_BRAND);
            
            if (isNonBrandType) {
                logger.info("Word '{}' exists in registry as a non-brand type, skipping", normalizedBrand);
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
     * Extracts a potential brand from the title by using heuristics
     * This is used when no brand was found in the registry
     */
    private String extractPotentialBrandFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        
        String cleanedTitle = textProcessor.cleanTitle(title);
        String[] words = cleanedTitle.split("\\s+");
        
        if (words.length > 0) {
            // First word is often the brand in product titles
            String firstWord = words[0].trim();
            
            // Basic validation - brands are usually at least 2 characters
            // and don't start with numbers
            if (firstWord.length() >= 2 && !Character.isDigit(firstWord.charAt(0))) {
                return firstWord.toLowerCase();
            }
            
            // If first word is very short or a number, try the second word
            if (words.length > 1) {
                String secondWord = words[1].trim();
                if (secondWord.length() >= 2 && !Character.isDigit(secondWord.charAt(0))) {
                    return secondWord.toLowerCase();
                }
            }
        }
        
        return null;
    }

    /**
     * Update all raw items with the same title to be processed and mapped to the same product
     * @param title The title to match
     * @param productId The product ID to set
     */
    private void updateAllItemsWithSameTitle(String title, Integer productId) {
        if (title == null || title.isEmpty() || productId == null) {
            return;
        }
        
        // In our new approach, we're handling items with the same title directly in the main process
        // This method is now used only for backward compatibility with other code paths
        List<CrawlerRaw> itemsWithSameTitle = crawlerRawRepository.findByTitle(title);
        
        // Skip items that are already processed to avoid redundant updates
        List<CrawlerRaw> unprocessedItems = itemsWithSameTitle.stream()
                .filter(item -> item.getProcessed() == null || !item.getProcessed())
                .collect(Collectors.toList());
                
        if (unprocessedItems.isEmpty()) {
            logger.debug("No additional unprocessed items found with title: {}", title);
            return;
        }
        
        logger.debug("Marking {} additional items with title '{}' as processed and mapped to product {}",
                unprocessedItems.size(), title, productId);
        
        int count = 0;
        for (CrawlerRaw item : unprocessedItems) {
            item.setProcessed(true);
            item.setMatchedProductId(productId);
            crawlerRawRepository.save(item);
            count++;
        }
        
        if (count > 0) {
            logger.debug("Updated {} additional items with title: {}", count, title);
        }
    }
}