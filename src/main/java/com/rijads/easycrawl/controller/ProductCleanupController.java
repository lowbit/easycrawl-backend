package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.service.ProductCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple controller to manually trigger product cleanup operations
 */
@RestController
@RequestMapping("/api/products/cleanup-manual")
public class ProductCleanupController {
    private static final Logger logger = LoggerFactory.getLogger(ProductCleanupController.class);

    @Autowired
    private ProductCleanupService productCleanupService;

    /**
     * Trigger manual cleanup of products
     * Updates product names and merges duplicates
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> runProductCleanup() {
        logger.info("Manual product cleanup requested");
        Map<String, Object> results = productCleanupService.manualProductCleanup();
        return ResponseEntity.ok(results);
    }

    /**
     * Trigger only the name standardization part
     */
    @PostMapping("/names")
    public ResponseEntity<Map<String, Object>> updateProductNames() {
        logger.info("Manual product name update requested");
        Map<String, Object> results = productCleanupService.updateProductNamesBasedOnRegistry();
        return ResponseEntity.ok(results);
    }

    /**
     * Trigger only the duplicate merging part
     */
    @PostMapping("/duplicates")
    public ResponseEntity<Map<String, Object>> mergeDuplicates() {
        logger.info("Manual duplicate merging requested");
        Map<String, Object> results = productCleanupService.mergeProductDuplicates();
        return ResponseEntity.ok(results);
    }
}