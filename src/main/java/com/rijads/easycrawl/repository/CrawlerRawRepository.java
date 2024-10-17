package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerRaw;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerRawRepository
        extends CrudRepository<CrawlerRaw, Integer>,
                PagingAndSortingRepository<CrawlerRaw, Integer>,
                JpaSpecificationExecutor<CrawlerRaw> {}
