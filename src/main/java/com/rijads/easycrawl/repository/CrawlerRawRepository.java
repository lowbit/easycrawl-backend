package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.dto.CrawlerRawDTO;
import com.rijads.easycrawl.model.CrawlerJob;
import com.rijads.easycrawl.model.CrawlerRaw;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlerRawRepository
        extends CrudRepository<CrawlerRaw, Integer>,
                PagingAndSortingRepository<CrawlerRaw, Integer>,
                JpaSpecificationExecutor<CrawlerRaw> {
    List<CrawlerRaw> getByJob(CrawlerJob job);
}
