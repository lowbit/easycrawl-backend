package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerRawDTO;
import com.rijads.easycrawl.mapper.CrawlerRawMapper;
import com.rijads.easycrawl.model.CrawlerJob;
import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.repository.CrawlerRawRepository;
import com.rijads.easycrawl.specification.CrawlerRawSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CrawlerRawService {
    private final CrawlerRawRepository repository;
    private final CrawlerRawMapper mapper;

    public CrawlerRawService(CrawlerRawRepository repository, CrawlerRawMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<CrawlerRawDTO> getAllCrawlerRaws(
            String configCode,
            String title,
            Double minPrice,
            Double maxPrice,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        Specification<CrawlerRaw> spec =
                Specification.where(CrawlerRawSpecification.hasConfigCode(configCode))
                        .and(CrawlerRawSpecification.titleContains(title))
                        .and(CrawlerRawSpecification.priceBetween(minPrice, maxPrice))
                        .and(CrawlerRawSpecification.createdBetween(createdFrom, createdTo));
        return repository.findAll(spec, pageable).map(mapper::toDto);
    }

    public Optional<CrawlerRawDTO> getCrawlerRawById(Integer id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public CrawlerRawDTO saveCrawlerRaw(CrawlerRawDTO crawlerRaw) {
        CrawlerRaw res = repository.save(mapper.dtoToEntity(crawlerRaw));
        return mapper.toDto(res);
    }

    public void deleteCrawlerRaw(Integer id) {
        repository.deleteById(id);
    }

    public List<CrawlerRawDTO> getAllCrawlerRawsByJobId(Integer id) {
        CrawlerJob job = new CrawlerJob();
        job.setId(id);
        return repository.getByJob(job).stream().map(mapper::toDto).toList();
    }
}
