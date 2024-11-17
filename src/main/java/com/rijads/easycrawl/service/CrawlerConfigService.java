package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.mapper.CrawlerConfigMapper;
import com.rijads.easycrawl.mapper.CrawlerWebsiteMapper;
import com.rijads.easycrawl.mapper.DropdownMapper;
import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.repository.CrawlerConfigRepository;
import com.rijads.easycrawl.repository.CrawlerWebsiteRepository;
import com.rijads.easycrawl.repository.ProductCategoryRepository;
import com.rijads.easycrawl.specification.CrawlerConfigSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrawlerConfigService {
    private final CrawlerConfigRepository repository;
    private final CrawlerConfigMapper mapper;

    private final CrawlerWebsiteRepository crawlerWebsiteRepository;
    private final DropdownMapper dropdownMapper;
    private final CrawlerWebsiteMapper crawlerWebsiteMapper;
    private final ProductCategoryRepository productCategoryRepository;

    public CrawlerConfigService(
            final CrawlerConfigRepository repository,
            final CrawlerConfigMapper mapper,
            final CrawlerWebsiteRepository crawlerWebsiteRepository,
            final DropdownMapper dropdownMapper,
            final CrawlerWebsiteMapper crawlerWebsiteMapper,
            final ProductCategoryRepository productCategoryRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.crawlerWebsiteRepository = crawlerWebsiteRepository;
        this.dropdownMapper = dropdownMapper;
        this.crawlerWebsiteMapper = crawlerWebsiteMapper;
        this.productCategoryRepository = productCategoryRepository;
    }

    public Page<CrawlerConfigDTO> getAllCrawlerConfigs(
            final String website,
            final String category,
            final LocalDateTime createdFrom,
            final LocalDateTime createdTo,
            final Pageable pageable) {
        Specification<CrawlerConfig> spec =
                Specification.where(CrawlerConfigSpecification.hasWebsite(website))
                        .and(CrawlerConfigSpecification.hasCategory(category))
                        .and(CrawlerConfigSpecification.createdBetween(createdFrom, createdTo));
        Page<CrawlerConfig> resEntities = repository.findAll(spec, pageable);
        Page<CrawlerConfigDTO> res = resEntities.map(entity -> mapper.toDto(entity));
        return res;
    }

    public List<DropdownDTO> getAllWebsitesDropdown() {
        List<CrawlerWebsite> resEntities = crawlerWebsiteRepository.findAll();
        return resEntities.stream()
                .map(crawlerWebsite -> dropdownMapper.crawlerWebsiteToDto(crawlerWebsite))
                .toList();
    }

    public List<DropdownDTO> getAllCategoriesDropdown() {
        List<ProductCategory> resEntities = productCategoryRepository.findAll();
        return resEntities.stream()
                .map(crawlerWebsite -> dropdownMapper.productCategoryToDto(crawlerWebsite))
                .toList();
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

    public ResponseEntity<CrawlerConfigDTO> addCrawlerConfig(final CrawlerConfigDTO request) {
        CrawlerConfig entity = mapper.toEntity(request);
        String code = request.getCrawlerWebsite() + '/' + request.getProductCategory();
        entity.setCode(code);
        entity.setCreated(LocalDateTime.now());
        entity.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        repository.save(entity);
        return ResponseEntity.ok(mapper.toDto(entity));
    }
}
