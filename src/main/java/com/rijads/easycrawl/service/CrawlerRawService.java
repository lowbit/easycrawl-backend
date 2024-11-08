package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.repository.CrawlerRawRepository;
import com.rijads.easycrawl.specification.CrawlerRawSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CrawlerRawService {
    private final CrawlerRawRepository repository;

    public CrawlerRawService(CrawlerRawRepository repository) {
        this.repository = repository;
    }

    public Page<CrawlerRaw> getAllCrawlerRaws(
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
        return repository.findAll(spec, pageable);
    }

    public Optional<CrawlerRaw> getCrawlerRawById(Integer id) {
        return repository.findById(id);
    }

    public CrawlerRaw saveCrawlerRaw(CrawlerRaw crawlerRaw) {
        return repository.save(crawlerRaw);
    }

    public void deleteCrawlerRaw(Integer id) {
        repository.deleteById(id);
    }
}
