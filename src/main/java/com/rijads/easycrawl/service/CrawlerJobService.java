package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerJobDto;
import com.rijads.easycrawl.mapper.CrawlerJobMapper;
import com.rijads.easycrawl.model.CrawlerJob;
import com.rijads.easycrawl.repository.CrawlerJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;


@Service
public class CrawlerJobService {
    private final CrawlerJobRepository repository;
    private final CrawlerJobMapper mapper;

    public CrawlerJobService(CrawlerJobRepository repository, CrawlerJobMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    public Page<CrawlerJobDto> getAllCrawlerJobs(final Pageable page){
        Specification<CrawlerJob> spec = (root, query, criteriaBuilder) -> null;
        Page<CrawlerJob> resEntities = repository.findAll(spec,page);
        return resEntities.map(mapper::toDto);
    }
}
