package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.service.ProductConsistencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for managing product consistency operations
 */
@RestController
@RequestMapping("/api/product-consistency")
public class ProductConsistencyController {

    @Autowired
    private ProductConsistencyService productConsistencyService;

    /**
     * Run a full product consistency check and update
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> runConsistencyCheck() {
        Map<String, Object> results = productConsistencyService.performProductConsistencyCheck();
        return ResponseEntity.ok(results);
    }

    /**
     * Update only brand and model information for products
     */
    @PostMapping("/update-brand-model")
    public ResponseEntity<Map<String, Object>> updateBrandAndModel() {
        ProductConsistencyService.ProductUpdateStats stats = productConsistencyService.updateProductBrandAndModel();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedCount", stats.getUpdatedCount());
        response.put("brandUpdates", stats.getBrandUpdates().size());
        response.put("modelUpdates", stats.getModelUpdates().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Identify and merge similar products
     */
    @PostMapping("/merge-similar")
    public ResponseEntity<Map<String, Object>> mergeSimilarProducts() {
        ProductConsistencyService.MergeStats stats = productConsistencyService.identifyAndMergeSimilarProducts();

        Map<String, Object> response = new HashMap<>();
        response.put("mergedGroupCount", stats.getMergedGroupCount());
        response.put("totalMergedCount", stats.getTotalMergedCount());
        response.put("errors", stats.getErrors().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Normalize product names
     */
    @PostMapping("/normalize-names")
    public ResponseEntity<Map<String, Object>> normalizeProductNames() {
        int updatedCount = productConsistencyService.normalizeProductNames();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedCount", updatedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Update product categories
     */
    @PostMapping("/update-categories")
    public ResponseEntity<Map<String, Object>> updateProductCategories() {
        int updatedCount = productConsistencyService.updateProductCategories();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedCount", updatedCount);

        return ResponseEntity.ok(response);
    }
}