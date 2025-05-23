package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.GroupedVariantDTO;
import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.PriceHistoryDTO;
import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductVariantDTO;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.service.ProductService;
import com.rijads.easycrawl.service.ProductVariantService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;
    private final ProductVariantService productVariantService;

    public ProductController(ProductService productService, ProductVariantService productVariantService) {
        this.productService = productService;
        this.productVariantService = productVariantService;
    }

    /** Search products with various filters - with pagination support */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductDTO>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            Pageable pageable) {
        Page<ProductDTO> res = productService.searchProducts(name, category, brand, pageable);
        return ResponseEntity.ok(res);
    }

    /** Get product details by ID */
    @GetMapping("/{productId}")
    public ResponseEntity<Page<ProductVariantDTO>> getProduct(
            @PathVariable Integer productId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String websiteCode,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String entitySize,
            @RequestParam(required = false) String property1,
            @RequestParam(required = false) String property2,
            @RequestParam(required = false) String property3,
            @RequestParam(required = false) String property4,
            Pageable pageable) {
        Page<ProductVariantDTO> res = productService.getProductById(productId, title, websiteCode, color, inStock, entitySize, property1, property2, property3, property4, pageable);
        return ResponseEntity.ok(res);
    }

    /**
     * Trigger product processing manually Updated to create a job instead of directly processing
     */
    @PostMapping("/process")
    public ResponseEntity<JobDTO> triggerProcessing() {
        try {
            JobDTO res = productService.triggerProductProcessing();
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/brands-list")
    public ResponseEntity<List<String>> getBrandsList() {
        return ResponseEntity.ok(productService.getBrandsList());
    }

    /**
     * Trigger processing for a specific category Updated to create a job instead of directly
     * processing
     */
    @PostMapping("/process/{category}")
    public ResponseEntity<JobDTO> triggerCategoryProcessing(@PathVariable String category) {
        try {
            JobDTO res = productService.triggerCategoryProcessing(category);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Trigger product cleanup Updated to create a job instead of directly processing */
    @PostMapping("/cleanup")
    public ResponseEntity<JobDTO> triggerCleanup(@RequestParam(required = false) String type) {
        JobDTO res = productService.triggerCleanup(type);
        return ResponseEntity.ok(res);
    }

    /**
     * Process a specific product directly This does not use the job system for single item
     * processing
     */
    @PostMapping("/process/item/{id}")
    public ResponseEntity<Map<String, Object>> processSpecificItem(@PathVariable Integer id) {
        try {
            boolean success = productService.processSpecificItem(id);
            Map<String, Object> result =
                    Map.of(
                            "success", success,
                            "id", id,
                            "processed", true);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "id", id, "error", e.getMessage()));
        }
    }

    @GetMapping("/categories")
    public List<ProductCategory> getAllProductCategories() {
        return productService.getAllProductGategories();
    }

    /**
     * Get grouped variants for a product with price history
     */
    @GetMapping("/{productId}/grouped-variants")
    public ResponseEntity<List<GroupedVariantDTO>> getGroupedVariants(
            @PathVariable Integer productId) {
        List<GroupedVariantDTO> groupedVariants = productVariantService.getGroupedVariants(productId);
        return ResponseEntity.ok(groupedVariants);
    }
    
    /**
     * Get price history for a specific variant
     */
    @GetMapping("/variants/{variantId}/price-history")
    public ResponseEntity<List<PriceHistoryDTO>> getVariantPriceHistory(
            @PathVariable Integer variantId) {
        List<PriceHistoryDTO> priceHistory = productVariantService.getVariantPriceHistory(variantId);
        return ResponseEntity.ok(priceHistory);
    }
    
    /**
     * Get price history for variants with the same title and website
     */
    @GetMapping("/variants/price-history")
    public ResponseEntity<List<PriceHistoryDTO>> getGroupedVariantPriceHistory(
            @RequestParam String title,
            @RequestParam String websiteCode) {
        List<PriceHistoryDTO> priceHistory = 
                productVariantService.getGroupedVariantPriceHistory(title, websiteCode);
        return ResponseEntity.ok(priceHistory);
    }
}
