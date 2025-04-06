package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.repository.ProductRegistryRepository;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/product/registry")
public class ProductRegistryController {
    @Autowired
    private ProductRegistryRepository registryRepository;

    @Autowired
    private ProductTextProcessor textProcessor;

    /**
     * Get all registry entries
     */
    @GetMapping
    public List<ProductRegistry> getAllRegistry() {
        return registryRepository.findAll();
    }

    /**
     * Get registry entries by type
     */
    @GetMapping("/type/{type}")
    public List<ProductRegistry> getRegistryByType(@PathVariable String type) {
        try {
            ProductRegistry.RegistryType registryType = ProductRegistry.RegistryType.valueOf(type.toUpperCase());
            return registryRepository.findByRegistryTypeAndEnabledTrue(registryType);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * Get all registry types
     */
    @GetMapping("/types")
    public List<String> getRegistryTypes() {
        return Arrays.stream(ProductRegistry.RegistryType.values())
                .map(Enum::name)
                .toList();
    }

    /**
     * Create new registry entry
     */
    @PostMapping
    public ResponseEntity<ProductRegistry> createRegistry(
            @RequestBody ProductRegistry registry,
            @RequestParam(defaultValue = "SYSTEM") String username) {

        ProductRegistry saved = registryRepository.save(registry);

        // Refresh the text processor cache
        textProcessor.refreshRegistry();

        return ResponseEntity.ok(saved);
    }

    /**
     * Update registry entry
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductRegistry> updateRegistry(
            @PathVariable Integer id,
            @RequestBody ProductRegistry registry,
            @RequestParam(defaultValue = "SYSTEM") String username) {

        Optional<ProductRegistry> existingOpt = registryRepository.findById(id);

        if (existingOpt.isPresent()) {
            ProductRegistry existing = existingOpt.get();
            existing.setRegistryKey(registry.getRegistryKey());
            existing.setRegistryValue(registry.getRegistryValue());
            existing.setDescription(registry.getDescription());
            existing.setEnabled(registry.getEnabled());

            ProductRegistry saved = registryRepository.save(existing);

            // Refresh the text processor cache
            textProcessor.refreshRegistry();

            return ResponseEntity.ok(saved);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Delete registry entry
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRegistry(@PathVariable Integer id) {
        if (registryRepository.existsById(id)) {
            registryRepository.deleteById(id);

            // Refresh the text processor cache
            textProcessor.refreshRegistry();

            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Force refresh of registry cache
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshRegistry() {
        textProcessor.refreshRegistry();
        return ResponseEntity.ok("Registry cache refreshed");
    }

    /**
     * Bulk import registry entries
     */
    @PostMapping("/bulk-import")
    public ResponseEntity<String> bulkImport(
            @RequestBody List<ProductRegistry> registries) {
        registryRepository.saveAll(registries);

        // Refresh the text processor cache
        textProcessor.refreshRegistry();

        return ResponseEntity.ok("Imported " + registries.size() + " registry entries");
    }
}
