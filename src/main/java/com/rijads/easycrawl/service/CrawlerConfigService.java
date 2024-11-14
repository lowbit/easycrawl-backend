package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.dto.CrawlerWebsiteDropdownDTO;
import com.rijads.easycrawl.mapper.CrawlerConfigMapper;
import com.rijads.easycrawl.mapper.CrawlerWebsiteMapper;
import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.repository.CrawlerConfigRepository;
import com.rijads.easycrawl.repository.CrawlerWebsiteRepository;
import com.rijads.easycrawl.specification.CrawlerConfigSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrawlerConfigService {
    private final CrawlerConfigRepository repository;
    private final CrawlerConfigMapper mapper;

    private final CrawlerWebsiteRepository crawlerWebsiteRepository;
    private final CrawlerWebsiteMapper crawlerWebsiteMapper;

    public CrawlerConfigService(
            final CrawlerConfigRepository repository,
            final CrawlerConfigMapper mapper,
            final CrawlerWebsiteRepository crawlerWebsiteRepository,
            final CrawlerWebsiteMapper crawlerWebsiteMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.crawlerWebsiteRepository = crawlerWebsiteRepository;
        this.crawlerWebsiteMapper = crawlerWebsiteMapper;
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

    public List<CrawlerWebsiteDropdownDTO> getAllWebsitesDropdown() {
        List<CrawlerWebsite> resEntities = crawlerWebsiteRepository.findAll();
        return resEntities.stream()
                .map(crawlerWebsite -> crawlerWebsiteMapper.toDropdownDto(crawlerWebsite))
                .toList();
    }
}
