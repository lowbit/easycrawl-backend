package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductDetailDTO;
import com.rijads.easycrawl.mapper.ProductMapper;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.model.ProductVariant;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.service.ProductMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class ProductController {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ProductMatchingService productMatchingService;

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
     */
    @PostMapping("/process")
    public ResponseEntity<String> triggerProcessing() {
        try {
            productMatchingService.manualProcessUnprocessedItems();
            return ResponseEntity.ok("Product processing started");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Trigger processing for a specific category
     */
    @PostMapping("/process/{category}")
    public ResponseEntity<String> triggerCategoryProcessing(@PathVariable String category) {
        try {
            productMatchingService.processItemsByCategory(category);
            return ResponseEntity.ok("Processing started for category: " + category);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
