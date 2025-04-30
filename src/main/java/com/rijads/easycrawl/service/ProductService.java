package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductVariantDTO;
import com.rijads.easycrawl.mapper.ProductMapper;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.model.ProductVariant;
import com.rijads.easycrawl.repository.ProductCategoryRepository;
import com.rijads.easycrawl.repository.ProductRepository;
import com.rijads.easycrawl.repository.ProductVariantRepository;
import com.rijads.easycrawl.specification.ProductSpecification;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductMatchingService productMatchingService;
    private final ProductCleanupService productCleanupService;
    private final JobService jobService;
    private final ProductMapper productMapper;
    private final ProductCategoryRepository repository;

    public ProductService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductMatchingService productMatchingService,
            ProductCleanupService productCleanupService,
            JobService jobService,
            ProductMapper productMapper,
            ProductCategoryRepository repository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.productMatchingService = productMatchingService;
        this.productCleanupService = productCleanupService;
        this.jobService = jobService;
        this.productMapper = productMapper;
        this.repository = repository;
    }

    public Page<ProductDTO> searchProducts(
            String name, String category, String brand, Pageable pageable) {
        Specification<Product> spec =
                Specification.where(ProductSpecification.hasBrand(brand))
                        .and(ProductSpecification.hasCategory(category))
                        .and(ProductSpecification.hasName(name));
        return productRepository.findAll(spec, pageable).map(productMapper::toDto);
    }

    public Page<ProductVariantDTO> getProductById(
            Integer productId,
            String title,
            String websiteCode,
            String color,
            Boolean inStock,
            String size,
            String property1,
            String property2,
            String property3,
            String property4,
            Pageable pageable) {
        Specification<ProductVariant> spec = Specification.where(null);
        if(productId != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("product").get("id"), productId));
        }
        if (title != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
        }
        if (websiteCode != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("website").get("code")), "%" + websiteCode.toLowerCase() + "%"));
        }
        if (color != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("color")), "%" + color.toLowerCase() + "%"));
        }
        if (size != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("size")), "%" + size.toLowerCase() + "%"));
        }
        if (inStock != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("inStock"), inStock));
        }
        if (property1 != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("property1")), "%" + property1.toLowerCase() + "%"));
        }
        if (property2 != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("property2")), "%" + property2.toLowerCase() + "%"));
        }
        if (property3 != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("property3")), "%" + property3.toLowerCase() + "%"));
        }
        if (property4 != null){
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("property4")), "%" + property4.toLowerCase() + "%"));
        }
        Page<ProductVariant> variants = variantRepository.findAll(spec, pageable);
        return variants.map(productMapper::variantToVariantDTO);
    }

    public JobDTO triggerProductProcessing() {
        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobType("PRODUCT_MAPPING");
        return jobService.create(jobDTO);
    }

    public List<String> getBrandsList() {
        return productRepository.findDistinctBrands();
    }

    public JobDTO triggerCategoryProcessing(String category) {
        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobType("PRODUCT_MAPPING");
        jobDTO.setParameters(category);
        return jobService.create(jobDTO);
    }

    public JobDTO triggerCleanup(String type) {
        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobType("PRODUCT_CLEANUP");
        jobDTO.setParameters(Objects.requireNonNullElse(type, "all"));
        return jobService.create(jobDTO);
    }

    public boolean processSpecificItem(Integer id) {
        var rawItemOpt = productMatchingService.getRawItemById(id);
        if (rawItemOpt.isEmpty()) {
            throw new EntityNotFoundException();
        }
        return productMatchingService.processRawProduct(rawItemOpt.get());
    }

    public List<ProductCategory> getAllProductGategories() {
        return repository.findAll();
    }
}
