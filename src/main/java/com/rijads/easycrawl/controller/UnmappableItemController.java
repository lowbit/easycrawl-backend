package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.AddBrandsRequest;
import com.rijads.easycrawl.service.UnmappableItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller to manage and analyze unmappable items
 */
@RestController
@RequestMapping("/api/unmappable")
public class UnmappableItemController {

    @Autowired
    private UnmappableItemService unmappableItemService;

    /**
     * Get statistics about unmappable items
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(unmappableItemService.getUnmappableItemStats());
    }

    /**
     * Analyze potential missing brands
     */
    @GetMapping("/analyze/brands")
    public ResponseEntity<List<Map<String, Object>>> analyzeBrands(
            @RequestParam(defaultValue = "3") int minFrequency) {
        return ResponseEntity.ok(unmappableItemService.analyzePotentialMissingBrands(minFrequency));
    }

    /**
     * Add brands to registry
     */
    @PostMapping("/brands/add")
    public ResponseEntity<Map<String, Object>> addBrands(@RequestBody AddBrandsRequest request) {
        int added = unmappableItemService.addPotentialBrandsToRegistry(
                request.getBrands(), request.getDescription());

        Map<String, Object> response = Map.of(
                "success", true,
                "added", added,
                "message", added + " brand(s) added to registry"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Retry processing unmappable items
     */
    @PostMapping("/retry")
    public ResponseEntity<Map<String, Object>> retryUnmappableItems(
            @RequestParam(defaultValue = "5") int maxAttempts) {
        Map<String, Object> result = unmappableItemService.retryUnmappableItems(maxAttempts);
        return ResponseEntity.ok(result);
    }

    /**
     * Get details about a specific unmappable item
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getItemDetails(@PathVariable Integer id) {
        Map<String, Object> details = unmappableItemService.getUnmappableItemDetails(id);

        if (details.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(details);
    }
}