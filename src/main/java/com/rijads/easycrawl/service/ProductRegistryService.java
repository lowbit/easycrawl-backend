package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.repository.ProductRegistryRepository;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.repository.CrawlerRawRepository;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.HashSet;
import java.util.Set;

@Service
public class ProductRegistryService {
    private static final Logger logger = LoggerFactory.getLogger(ProductRegistryService.class);
    
    private final ProductRegistryRepository registryRepository;
    private final ProductTextProcessor textProcessor;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CrawlerRawRepository crawlerRawRepository;

    public ProductRegistryService(
            ProductRegistryRepository registryRepository,
            ProductTextProcessor textProcessor,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            CrawlerRawRepository crawlerRawRepository) {
        this.registryRepository = registryRepository;
        this.textProcessor = textProcessor;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.crawlerRawRepository = crawlerRawRepository;
    }
    public Page<ProductRegistry> getAll(String type, String search, Pageable page) {
        Specification<ProductRegistry> spec = Specification.where(null);
        if (type != null && !type.isEmpty()) {
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("registryType"), type));
        }
        if (search != null && !search.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("registryKey")), "%" + search.toLowerCase() + "%"));
        }
        return registryRepository.findAll(spec,page);
    }

    public ProductRegistry create(String username, ProductRegistry registry) {
        ProductRegistry entity = registryRepository.save(registry);
        textProcessor.refreshRegistry();
        return entity;
    }

    @Transactional
    public ProductRegistry update(String username, Integer id, ProductRegistry registry) {
        Optional<ProductRegistry> existingOpt = registryRepository.findById(id);
        if (existingOpt.isPresent()) {
            ProductRegistry existing = existingOpt.get();
            
            // Check if we're updating a BRAND registry
            boolean wasBrand = existing.getRegistryType() == ProductRegistry.RegistryType.BRAND;
            boolean stillBrand = registry.getRegistryType() == ProductRegistry.RegistryType.BRAND;
            boolean brandNameChanged = wasBrand && stillBrand && 
                    !existing.getRegistryKey().equals(registry.getRegistryKey());
            
            // Handle brand removal if:
            // 1. It was a brand but no longer is, or
            // 2. It's still a brand but the brand name changed
            if ((wasBrand && !stillBrand) || brandNameChanged) {
                logger.info("Brand registry being changed or removed: {}", existing.getRegistryKey());
                handleBrandRemoval(existing.getRegistryKey());
            }
            
            existing.setRegistryKey(registry.getRegistryKey());
            existing.setRegistryType(registry.getRegistryType());
            existing.setRegistryValue(registry.getRegistryValue());
            existing.setDescription(registry.getDescription());
            existing.setEnabled(registry.getEnabled());

            ProductRegistry res = registryRepository.save(existing);
            // Refresh the text processor cache
            textProcessor.refreshRegistry();
            return res;
        }
        return null;
    }
    
    @Transactional
    public void delete(Integer id) {
        Optional<ProductRegistry> registryOpt = registryRepository.findById(id);
        if (registryOpt.isPresent()) {
            ProductRegistry registry = registryOpt.get();
            
            // Check if we're deleting a BRAND registry
            if (registry.getRegistryType() == ProductRegistry.RegistryType.BRAND) {
                logger.info("Brand registry being deleted: {}", registry.getRegistryKey());
                handleBrandRemoval(registry.getRegistryKey());
            }
            
            registryRepository.deleteById(id);
            textProcessor.refreshRegistry();
        }
    }

    public void refreshCache() {
        textProcessor.refreshRegistry();
    }

    public void bulkImport(List<ProductRegistry> registries) {
        registryRepository.saveAll(registries);
        // Refresh the text processor cache
        textProcessor.refreshRegistry();
    }

    public List<ProductRegistry> bulkExport() {
        return (List<ProductRegistry>) registryRepository.findAll();
    }
    
    /**
     * Batch change registry type for multiple entries
     * If changing from BRAND to another type, this will also:
     * - Remove all products with that brand
     * - Remove all variants of those products 
     * - Mark crawler raw items that were mapped to those products as unprocessed
     * 
     * @param username User performing the change
     * @param ids List of registry IDs to change
     * @param targetType Target registry type
     * @return Number of entries successfully updated
     */
    @Transactional
    public int batchChangeType(String username, List<Integer> ids, ProductRegistry.RegistryType targetType) {
        if (ids == null || ids.isEmpty() || targetType == null) {
            return 0;
        }
        
        // Convert Iterable to List using StreamSupport
        Iterable<ProductRegistry> registriesIterable = registryRepository.findAllById(ids);
        List<ProductRegistry> registries = new ArrayList<>();
        registriesIterable.forEach(registries::add);
        
        if (registries.isEmpty()) {
            return 0;
        }
        
        int updatedCount = 0;
        for (ProductRegistry registry : registries) {
            // Skip if registry is already of the target type
            if (registry.getRegistryType() == targetType) {
                continue;
            }
            
            // Special handling for changing from BRAND to another type
            if (registry.getRegistryType() == ProductRegistry.RegistryType.BRAND) {
                String brandName = registry.getRegistryKey();
                handleBrandRemoval(brandName);
            }
            
            registry.setRegistryType(targetType);
            updatedCount++;
        }
        
        // Save all modified entries if any were changed
        if (updatedCount > 0) {
            registryRepository.saveAll(registries);
            // Refresh the text processor cache since registry types have changed
            textProcessor.refreshRegistry();
        }
        
        return updatedCount;
    }
    
    /**
     * Handle the removal of a brand from the registry
     * This will remove all products with that brand, their variants,
     * and mark related crawler raw items as unprocessed
     */
    private void handleBrandRemoval(String brandName) {
        logger.info("Handling brand removal for: {}", brandName);
        
        // 1. Find all products with this brand
        List<Product> productsToRemove = productRepository.findByBrandOrderByIdDesc(brandName);
        if (productsToRemove.isEmpty()) {
            logger.info("No products found with brand: {}", brandName);
            return;
        }
        
        logger.info("Found {} products with brand: {}", productsToRemove.size(), brandName);
        
        // 2. Collect all product IDs for later use
        List<Integer> productIds = new ArrayList<>();
        for (Product product : productsToRemove) {
            productIds.add(product.getId());
        }
        
        // 3. Get all unique titles from raw items directly mapped to these products
        Set<String> uniqueTitles = new HashSet<>();
        int directItemsCount = 0;
        
        for (Integer productId : productIds) {
            List<CrawlerRaw> directItems = crawlerRawRepository.findByMatchedProductId(productId);
            directItemsCount += directItems.size();
            
            for (CrawlerRaw item : directItems) {
                if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                    uniqueTitles.add(item.getTitle());
                }
            }
        }
        
        logger.info("Found {} directly mapped raw items with {} unique titles", 
                directItemsCount, uniqueTitles.size());
        
        // 4. Use bulk update to reset all items with these titles in a single query
        if (!uniqueTitles.isEmpty()) {
            List<String> titlesList = new ArrayList<>(uniqueTitles);
            int updatedCount = crawlerRawRepository.bulkResetByTitles(titlesList);
            logger.info("Reset {} total raw items by title in bulk", updatedCount);
        }
        
        // 5. Delete all product variants (must be done before deleting products)
        for (Product product : productsToRemove) {
            productVariantRepository.deleteByProduct(product);
        }
        
        // 6. Finally, delete all products with this brand
        productRepository.deleteAll(productsToRemove);
        
        logger.info("Successfully removed {} products with brand: {}", productIds.size(), brandName);
    }
}
