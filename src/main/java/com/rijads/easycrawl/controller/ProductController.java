package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductDetailDTO;
import com.rijads.easycrawl.mapper.ProductMapper;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.model.ProductVariant;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.service.JobService;
import com.rijads.easycrawl.service.ProductCleanupService;
import com.rijads.easycrawl.service.ProductMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ProductMatchingService productMatchingService;

    @Autowired
    private ProductCleanupService productCleanupService;

    @Autowired
    private JobService jobService;

    @Autowired
    private ProductMapper productMapper;

    /**
     * Search products with various filters
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductDTO>> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        // This would need a custom query method in the repository
        // For simplicity, we'll just use the basic search for now
        List<Product> products;

        if (query != null && !query.isEmpty()) {
            products = productRepository.searchProducts(query);
        } else if (category != null && !category.isEmpty()) {
            products = productRepository.findByCategory(new ProductCategory(category));
        } else if (brand != null && !brand.isEmpty()) {
            products = productRepository.findByBrand(brand);
        } else {
            products = productRepository.findAll(pageable).getContent();
        }

        List<ProductDTO> productDtos = products.stream()
                .map(product -> productMapper.toDto(product))
                .collect(Collectors.toList());

        return ResponseEntity.ok(productDtos);
    }

    /**
     * Get product details by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailDTO> getProduct(@PathVariable Integer id) {
        Optional<Product> productOpt = productRepository.findById(id);

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            List<ProductVariant> variants = variantRepository.findByProductId(id);

            ProductDetailDTO dto = productMapper.productAndVariantsToProductDetailDTO(product,variants);
            return ResponseEntity.ok(dto);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Trigger product processing manually
     * Updated to create a job instead of directly processing
     */
    @PostMapping("/process")
    public ResponseEntity<JobDTO> triggerProcessing() {
        try {
            // Create a product mapping job
            JobDTO jobDTO = new JobDTO();
            jobDTO.setJobType("PRODUCT_MAPPING");

            // Create the job
            JobDTO createdJob = jobService.create(jobDTO);

            return ResponseEntity.ok(createdJob);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Trigger processing for a specific category
     * Updated to create a job instead of directly processing
     */
    @PostMapping("/process/{category}")
    public ResponseEntity<JobDTO> triggerCategoryProcessing(@PathVariable String category) {
        try {
            // Create a product mapping job for this category
            JobDTO jobDTO = new JobDTO();
            jobDTO.setJobType("PRODUCT_MAPPING");
            jobDTO.setParameters(category);

            // Create the job
            JobDTO createdJob = jobService.create(jobDTO);

            return ResponseEntity.ok(createdJob);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Trigger product cleanup
     * Updated to create a job instead of directly processing
     */
    @PostMapping("/cleanup")
    public ResponseEntity<JobDTO> triggerCleanup(
            @RequestParam(required = false) String type) {

        try {
            // Create a product cleanup job
            JobDTO jobDTO = new JobDTO();
            jobDTO.setJobType("PRODUCT_CLEANUP");

            // Set parameters based on the type requested
            if (type != null) {
                jobDTO.setParameters(type);
            } else {
                jobDTO.setParameters("all");
            }

            // Create the job
            JobDTO createdJob = jobService.create(jobDTO);

            return ResponseEntity.ok(createdJob);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Process a specific product directly
     * This does not use the job system for single item processing
     */
    @PostMapping("/process/item/{id}")
    public ResponseEntity<Map<String, Object>> processSpecificItem(@PathVariable Integer id) {
        try {
            // Get the raw item
            var rawItemOpt = productMatchingService.getRawItemById(id);
            if (rawItemOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Process it directly
            boolean success = productMatchingService.processRawProduct(rawItemOpt.get());

            Map<String, Object> result = Map.of(
                    "success", success,
                    "id", id,
                    "processed", true
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "id", id,
                    "error", e.getMessage()
            ));
        }
    }
}