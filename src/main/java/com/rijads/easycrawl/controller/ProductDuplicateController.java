package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.service.ProductMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing product duplicates
 */
@RestController
@RequestMapping("/api/products/duplicates")
public class ProductDuplicateController {
    private static final Logger logger = LoggerFactory.getLogger(ProductDuplicateController.class);

    @Autowired
    private ProductMatchingService productMatchingService;

    /**
     * Find potential duplicate products based on brand and model similarity
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> findPotentialDuplicates() {
        logger.info("Finding potential duplicate products");
        List<Map<String, Object>> duplicates = productMatchingService.findPotentialDuplicates();
        logger.info("Found {} potential duplicate product groups", duplicates.size());
        return ResponseEntity.ok(duplicates);
    }

    /**
     * Merge two duplicate products - moving all variants from source to target
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeProducts(@RequestBody MergeRequest request) {
        logger.info("Merging product {} into product {}", request.getSourceProductId(), request.getTargetProductId());

        if (request.getSourceProductId() == null || request.getTargetProductId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Source and target product IDs are required"
            ));
        }

        boolean success = productMatchingService.mergeProducts(request.getSourceProductId(), request.getTargetProductId());

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Products merged successfully",
                    "sourceProductId", request.getSourceProductId(),
                    "targetProductId", request.getTargetProductId()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to merge products",
                    "sourceProductId", request.getSourceProductId(),
                    "targetProductId", request.getTargetProductId()
            ));
        }
    }

    /**
     * Data class for merge requests
     */
    public static class MergeRequest {
        private Integer sourceProductId;
        private Integer targetProductId;

        public Integer getSourceProductId() {
            return sourceProductId;
        }

        public void setSourceProductId(Integer sourceProductId) {
            this.sourceProductId = sourceProductId;
        }

        public Integer getTargetProductId() {
            return targetProductId;
        }

        public void setTargetProductId(Integer targetProductId) {
            this.targetProductId = targetProductId;
        }
    }
}