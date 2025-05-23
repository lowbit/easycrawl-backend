package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.service.ProductRegistryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/product/registry")
public class ProductRegistryController {

    private final ProductRegistryService productRegistryService;

    public ProductRegistryController(ProductRegistryService productRegistryService) {
        this.productRegistryService = productRegistryService;
    }

    /** Get all registry entries */
    @GetMapping
    public Page<ProductRegistry> getAll(
            @RequestParam(required = false) final String type,
            @RequestParam(required = false) final String search,
            final Pageable pageable) {
        return productRegistryService.getAll(type, search, pageable);
    }

    /** Get all registry types */
    @GetMapping("/types")
    public List<String> getRegistryTypes() {
        return Arrays.stream(ProductRegistry.RegistryType.values()).map(Enum::name).toList();
    }

    /** Create new registry entry */
    @PostMapping
    public ResponseEntity<ProductRegistry> createRegistry(
            @RequestBody ProductRegistry registry,
            @RequestParam(defaultValue = "SYSTEM") String username) {
        ProductRegistry res = productRegistryService.create(username, registry);
        return ResponseEntity.ok(res);
    }

    /** Update registry entry */
    @PutMapping("/{id}")
    public ResponseEntity<ProductRegistry> updateRegistry(
            @PathVariable Integer id,
            @RequestBody ProductRegistry registry,
            @RequestParam(defaultValue = "SYSTEM") String username) {
        ProductRegistry res = productRegistryService.update(username, id, registry);
        if (res != null) {
            return ResponseEntity.ok(res);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /** Delete registry entry */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRegistry(@PathVariable Integer id) {
        productRegistryService.delete(id);
        return ResponseEntity.notFound().build();
    }

    /** Force refresh of registry cache */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshRegistry() {
        productRegistryService.refreshCache();
        return ResponseEntity.ok("Registry cache refreshed");
    }

    /** Batch import registry entries */
    @PostMapping("/bulk-import")
    public ResponseEntity<String> bulkImport(@RequestBody List<ProductRegistry> registries) {
        productRegistryService.bulkImport(registries);
        return ResponseEntity.ok("Imported " + registries.size() + " registry entries");
    }
    /** Bulk import registry entries */

    @GetMapping("/bulk-export")
    public ResponseEntity<List<ProductRegistry>> bulkExport() {
        List<ProductRegistry> res = productRegistryService.bulkExport();
        return ResponseEntity.ok(res);
    }
    
    /** Batch change registry type for multiple entries */
    @PostMapping("/batch-change-type")
    public ResponseEntity<String> batchChangeType(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "SYSTEM") String username) {
        
        List<Integer> ids = (List<Integer>) request.get("ids");
        String targetType = (String) request.get("targetType");
        
        if (ids == null || targetType == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid request: ids and targetType are required");
        }
        
        try {
            ProductRegistry.RegistryType type = ProductRegistry.RegistryType.valueOf(targetType);
            int updatedCount = productRegistryService.batchChangeType(username, ids, type);
            return ResponseEntity.ok("Changed registry type to " + targetType + " for " + updatedCount + " entries");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid registry type: " + targetType);
        }
    }
}
