package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.GroupedVariantDTO;
import com.rijads.easycrawl.dto.PriceHistoryDTO;
import com.rijads.easycrawl.dto.ProductVariantDTO;
import com.rijads.easycrawl.mapper.PriceHistoryMapper;
import com.rijads.easycrawl.mapper.ProductMapper;
import com.rijads.easycrawl.model.PriceHistory;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import com.rijads.easycrawl.repository.PriceHistoryRepository;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductVariantService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductVariantService.class);
    
    private final ProductVariantRepository variantRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    
    public ProductVariantService(
            ProductVariantRepository variantRepository,
            PriceHistoryRepository priceHistoryRepository,
            ProductRepository productRepository,
            ProductMapper productMapper,
            PriceHistoryMapper priceHistoryMapper) {
        this.variantRepository = variantRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.priceHistoryMapper = priceHistoryMapper;
    }
    
    /**
     * Get all price history for a variant
     */
    public List<PriceHistoryDTO> getVariantPriceHistory(Integer variantId) {
        Optional<ProductVariant> variantOpt = variantRepository.findById(variantId);
        if (variantOpt.isEmpty()) {
            throw new EntityNotFoundException("Variant not found");
        }
        
        List<PriceHistory> history = priceHistoryRepository.findByVariantOrderByRecordedAtDesc(variantOpt.get());
        return priceHistoryMapper.toDtoList(history);
    }
    
    /**
     * Get price history for variants with the same title and website code
     */
    public List<PriceHistoryDTO> getGroupedVariantPriceHistory(String title, String websiteCode) {
        List<PriceHistory> history = priceHistoryRepository.findByTitleAndWebsiteCodeOrderByRecordedAtDesc(
                title, websiteCode);
        return priceHistoryMapper.toDtoList(history);
    }
    
    /**
     * Get grouped variants for a product
     * This groups variants by title and website code
     */
    public List<GroupedVariantDTO> getGroupedVariants(Integer productId) {
        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        
        // Get unique title-website combinations
        List<Map<String, String>> titleWebsiteCombos = 
                variantRepository.findDistinctTitleAndWebsiteByProductId(productId);
        
        // Create grouped variants
        List<GroupedVariantDTO> result = new ArrayList<>();
        
        for (Map<String, String> combo : titleWebsiteCombos) {
            String title = combo.get("title");
            String websiteCode = combo.get("websiteCode");
            
            // Get all variants with this title and website
            List<ProductVariant> variants = variantRepository.findByTitleAndWebsiteCode(title, websiteCode);
            
            if (!variants.isEmpty()) {
                // Create a grouped variant
                GroupedVariantDTO groupedVariant = createGroupedVariant(variants, title, websiteCode);
                result.add(groupedVariant);
            }
        }
        
        return result;
    }
    
    /**
     * Create a grouped variant from a list of variants with the same title and website
     */
    private GroupedVariantDTO createGroupedVariant(
            List<ProductVariant> variants, String title, String websiteCode) {
        
        GroupedVariantDTO dto = new GroupedVariantDTO();
        dto.setTitle(title);
        dto.setWebsiteCode(websiteCode);
        
        // Get the most recent variant for current data
        variants.sort(Comparator.comparing(ProductVariant::getModified).reversed());
        ProductVariant latest = variants.get(0);
        
        // Set current information from latest variant
        dto.setWebsiteName(latest.getWebsite().getName());
        dto.setColor(latest.getColor());
        dto.setSize(latest.getSize());
        dto.setCurrentPrice(latest.getPrice());
        dto.setSourceUrl(latest.getSourceUrl());
        dto.setImageUrl(latest.getImageUrl());
        dto.setVariantCount(variants.size());
        dto.setLastUpdated(latest.getModified().format(DateTimeFormatter.ISO_DATE_TIME));
        
        // Calculate min/max prices
        BigDecimal minPrice = variants.stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        
        BigDecimal maxPrice = variants.stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
        
        dto.setLowestPrice(minPrice);
        dto.setHighestPrice(maxPrice);
        
        // Get price history
        List<Integer> variantIds = variants.stream()
                .map(ProductVariant::getId)
                .collect(Collectors.toList());
        
        if (!variantIds.isEmpty()) {
            List<PriceHistory> priceHistory = priceHistoryRepository.findByVariantIdsOrderByRecordedAtDesc(variantIds);
            dto.setPriceHistory(priceHistoryMapper.toDtoList(priceHistory));
        } else {
            dto.setPriceHistory(Collections.emptyList());
        }
        
        return dto;
    }
    
    /**
     * Record price history for a variant
     * This is called when a variant's price changes
     * Creates one entry per day per variant, while preserving historical data
     */
    @Transactional
    public void recordPriceHistory(ProductVariant variant) {
        // Use current time as fallback
        recordPriceHistory(variant, LocalDateTime.now());
    }
    
    /**
     * Record price history for a variant with a specific timestamp
     * This allows recording price history with the crawl time
     */
    @Transactional
    public void recordPriceHistory(ProductVariant variant, LocalDateTime recordTime) {
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
                logger.debug("Updated existing price history for variant {} with new price information for date {}",
                        variant.getId(), recordTime.toLocalDate());
            } else {
                logger.debug("No price change for variant {} on date {}, keeping existing history entry",
                        variant.getId(), recordTime.toLocalDate());
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
            logger.debug("Created new price history for variant {} for date {}",
                    variant.getId(), recordTime.toLocalDate());
        }
    }
} 