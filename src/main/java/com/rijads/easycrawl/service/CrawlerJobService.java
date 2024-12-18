package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerErrorDTO;
import com.rijads.easycrawl.dto.CrawlerJobDTO;
import com.rijads.easycrawl.mapper.CrawlerJobMapper;
import com.rijads.easycrawl.model.CrawlerJob;
import com.rijads.easycrawl.repository.CrawlerErrorRepository;
import com.rijads.easycrawl.repository.CrawlerJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
public class CrawlerJobService {
    private final CrawlerJobRepository repository;
    private final CrawlerErrorRepository crawlerErrorRepository;
    private final CrawlerJobMapper mapper;

    public CrawlerJobService(CrawlerJobRepository repository, CrawlerErrorRepository crawlerErrorRepository, CrawlerJobMapper mapper) {
        this.repository = repository;
        this.crawlerErrorRepository = crawlerErrorRepository;
        this.mapper = mapper;
    }
    public Page<CrawlerJobDTO> getAllCrawlerJobs(final Pageable page){
        Specification<CrawlerJob> spec = (root, query, criteriaBuilder) -> null;
        Page<CrawlerJob> resEntities = repository.findAll(spec,page);
        return resEntities.map(mapper::toDto);
    }

    public List<CrawlerErrorDTO> getAllCrawlerJobErrors(Long id) {
        return crawlerErrorRepository.findAllByJob_Id(Math.toIntExact(id)).stream().map(mapper::errorToDto).toList();
    }

    public CrawlerJobDTO create(CrawlerJobDTO crawlerJobDTO) {
        CrawlerJob entity = mapper.toEntity(crawlerJobDTO);
        entity.setCreated(LocalDateTime.now());
        entity.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        entity.setStatus("Created");
        return mapper.toDto(repository.save(entity));
    }

    public CrawlerJobDTO getCrawlJobById(String id) {
        Optional<CrawlerJob> entity = repository.findById(id);
        return entity.map(mapper::toDto).orElse(null);
    }
}
