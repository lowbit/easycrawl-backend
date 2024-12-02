package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerJob;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface CrawlerJobRepository
        extends CrudRepository<CrawlerJob, String>,
                PagingAndSortingRepository<CrawlerJob, String>,
                JpaSpecificationExecutor<CrawlerJob> {

}
