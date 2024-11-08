package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerConfig;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerConfigRepository
        extends CrudRepository<CrawlerConfig, String>,
                PagingAndSortingRepository<CrawlerConfig, String>,
                JpaSpecificationExecutor<CrawlerConfig> {}
