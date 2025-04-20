package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductDetailDTO;
import com.rijads.easycrawl.mapper.ProductMapper;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.service.JobService;
import com.rijads.easycrawl.service.ProductCleanupService;
import com.rijads.easycrawl.service.ProductMatchingService;
import com.rijads.easycrawl.specification.ProductSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Search products with various filters - with pagination support
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductDTO>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            Pageable pageable) {
        Specification<Product> spec = Specification.where(
                ProductSpecification.hasBrand(brand))
                .and(ProductSpecification.hasCategory(category))
                .and(ProductSpecification.hasName(name));
        Page<ProductDTO> productDtoPage = productRepository.findAll(spec, pageable).map(productMapper::toDto);
        return ResponseEntity.ok(productDtoPage);
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
    @GetMapping("/brands-list")
    public ResponseEntity<List<String>> getBrandsList() {
        List<String> brands = productRepository.findDistinctBrands();
        return ResponseEntity.ok(brands);
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