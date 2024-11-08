package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.mapper.CrawlerConfigMapper;
import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.repository.CrawlerConfigRepository;
import com.rijads.easycrawl.specification.CrawlerConfigSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CrawlerConfigService {
    private final CrawlerConfigRepository repository;
    private final CrawlerConfigMapper mapper;

    public CrawlerConfigService(
            final CrawlerConfigRepository repository, final CrawlerConfigMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
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
}
