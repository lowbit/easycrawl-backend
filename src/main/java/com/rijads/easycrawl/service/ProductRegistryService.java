package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.ProductRegistry;
import com.rijads.easycrawl.repository.ProductRegistryRepository;
import com.rijads.easycrawl.utility.ProductTextProcessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductRegistryService {
    private final ProductRegistryRepository registryRepository;
    private final ProductTextProcessor textProcessor;

    public ProductRegistryService(ProductRegistryRepository registryRepository,
                                  ProductTextProcessor textProcessor) {
        this.registryRepository = registryRepository;
        this.textProcessor = textProcessor;
    }
    public Page<ProductRegistry> getAll(String type, String search, Pageable page) {
        Specification<ProductRegistry> spec = Specification.where(null);
        if (type != null && !type.isEmpty()) {
            spec = spec.and((root,query,criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("registryType"), type));
        }
        if (search != null && !search.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("registryKey")), "%" + search.toLowerCase() + "%"));
        }
        return registryRepository.findAll(spec,page);
    }

    public ProductRegistry create(String username, ProductRegistry registry) {
        ProductRegistry entity = registryRepository.save(registry);
        textProcessor.refreshRegistry();
        return entity;
    }

    public ProductRegistry update(String username, Integer id, ProductRegistry registry) {
        Optional<ProductRegistry> existingOpt = registryRepository.findById(id);
        if (existingOpt.isPresent()) {
            ProductRegistry existing = existingOpt.get();
            existing.setRegistryKey(registry.getRegistryKey());
            existing.setRegistryType(registry.getRegistryType());
            existing.setRegistryValue(registry.getRegistryValue());
            existing.setDescription(registry.getDescription());
            existing.setEnabled(registry.getEnabled());

            ProductRegistry res = registryRepository.save(existing);
            // Refresh the text processor cache
            textProcessor.refreshRegistry();
            return res;
        }
        return null;
    }
    public void delete(Integer id) {
        if (registryRepository.existsById(id)) {
            registryRepository.deleteById(id);
            textProcessor.refreshRegistry();
        }
    }

    public void refreshCache() {
        textProcessor.refreshRegistry();
    }

    public void bulkImport(List<ProductRegistry> registries) {
        registryRepository.saveAll(registries);
        // Refresh the text processor cache
        textProcessor.refreshRegistry();
    }

    public List<ProductRegistry> bulkExport() {
        return (List<ProductRegistry>) registryRepository.findAll();
    }
}
