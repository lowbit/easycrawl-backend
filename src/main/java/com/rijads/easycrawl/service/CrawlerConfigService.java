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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrawlerConfigService {
    private final CrawlerConfigRepository repository;
    private final CrawlerConfigMapper mapper;
    private final DropdownMapper dropdownMapper;

    public CrawlerConfigService(
            final CrawlerConfigRepository repository,
            final CrawlerConfigMapper mapper,
            final DropdownMapper dropdownMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.dropdownMapper = dropdownMapper;
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
        return resEntities.map(mapper::toDto);
    }

    public ResponseEntity<CrawlerConfigDTO> addCrawlerConfig(final CrawlerConfigDTO request) {
        CrawlerConfig entity = mapper.toEntity(request);
        String code = request.getCrawlerWebsite() + '/' + request.getProductCategory();
        if (repository.existsById(code)) {
            return new ResponseEntity<>(null, HttpStatus.CONFLICT);
        }
        entity.setCode(code);
        entity.setCreated(LocalDateTime.now());
        entity.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        repository.save(entity);
        return ResponseEntity.ok(mapper.toDto(entity));
    }

    public ResponseEntity<CrawlerConfigDTO> editCrawlerConfig(
            final String code, final CrawlerConfigDTO request) {
        CrawlerConfig entity = repository.findById(code).orElse(null);
        if (entity != null) {
            entity = mapper.toEntity(request);
            entity.setModified(LocalDateTime.now());
            entity.setModifiedBy(SecurityContextHolder.getContext().getAuthentication().getName());
            repository.save(entity);
            return ResponseEntity.ok(mapper.toDto(entity));
        }
        return ResponseEntity.notFound().build();
    }

    public ResponseEntity<Void> deleteCrawlerConfig(final String code) {
        if (repository.existsById(code)) {
            repository.deleteById(code);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    public ResponseEntity<Void> deleteCrawlerConfigs(final List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Iterable<CrawlerConfig> existingEntities = repository.findAllById(codes);
        List<CrawlerConfig> existingList = new ArrayList<>();
        existingEntities.forEach(existingList::add);

        if (existingList.size() == codes.size()) {
            repository.deleteAllById(codes);
            return ResponseEntity.ok().build();
        } else {
            List<String> existingCodes = existingList.stream().map(CrawlerConfig::getCode).toList();

            List<String> missingCodes =
                    codes.stream()
                            .filter(code -> !existingCodes.contains(code))
                            .collect(Collectors.toList());

            return ResponseEntity.notFound()
                    .header("Missing-Codes", String.join(",", missingCodes))
                    .build();
        }
    }

    public List<DropdownDTO> getAllCrawlerConfigsDropdown(String websiteCode) {
        List<CrawlerConfig> entities =
                (List<CrawlerConfig>) repository.findAllByCrawlerWebsite_Code(websiteCode);
        return entities.stream().map(dropdownMapper::crawlerConfigToDto).toList();
    }
}
