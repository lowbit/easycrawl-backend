package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.mapper.CrawlerWebsiteMapper;
import com.rijads.easycrawl.mapper.DropdownMapper;
import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.repository.CrawlerWebsiteRepository;
import com.rijads.easycrawl.repository.ProductCategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RegistryService {
    private final CrawlerWebsiteRepository crawlerWebsiteRepository;
    private final DropdownMapper dropdownMapper;
    private final ProductCategoryRepository productCategoryRepository;
    private final CrawlerWebsiteMapper crawlerWebsiteMapper;

    public RegistryService(
            CrawlerWebsiteRepository crawlerWebsiteRepository,
            DropdownMapper dropdownMapper,
            ProductCategoryRepository productCategoryRepository,
            CrawlerWebsiteMapper crawlerWebsiteMapper) {
        this.crawlerWebsiteRepository = crawlerWebsiteRepository;
        this.dropdownMapper = dropdownMapper;
        this.productCategoryRepository = productCategoryRepository;
        this.crawlerWebsiteMapper = crawlerWebsiteMapper;
    }

    public List<DropdownDTO> getAllWebsitesDropdown() {
        List<CrawlerWebsite> resEntities = crawlerWebsiteRepository.findAll();
        return resEntities.stream().map(dropdownMapper::crawlerWebsiteToDto).toList();
    }

    public List<DropdownDTO> getAllCategoriesDropdown() {
        List<ProductCategory> resEntities = productCategoryRepository.findAll();
        return resEntities.stream().map(dropdownMapper::productCategoryToDto).toList();
    }

    public ResponseEntity<DropdownDTO> addProductCategory(final DropdownDTO request) {
        ProductCategory entity = dropdownMapper.dtoToProductCategory(request);
        productCategoryRepository.save(entity);
        return ResponseEntity.ok(dropdownMapper.productCategoryToDto(entity));
    }

    public ResponseEntity<CrawlerWebsiteDTO> addCrawlerWebsite(final CrawlerWebsiteDTO request) {
        CrawlerWebsite entity = crawlerWebsiteMapper.dtoToEntity(request);
        entity.setCreated(LocalDateTime.now());
        entity.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        crawlerWebsiteRepository.save(entity);
        return ResponseEntity.ok(crawlerWebsiteMapper.toDto(entity));
    }

    public ResponseEntity<Void> deleteProductCategory(String id) {
        productCategoryRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<Void> deleteCrawlerWebsite(String id) {
        crawlerWebsiteRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
