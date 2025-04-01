package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.CrawlerJob;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

public interface CrawlerJobRepository
        extends CrudRepository<CrawlerJob, String>,
                PagingAndSortingRepository<CrawlerJob, String>,
                JpaSpecificationExecutor<CrawlerJob> {

    @Query(value = "SELECT * FROM crawler_job where config_code = :configCode AND (test_run is NULL OR test_run is false) and status = 'Finished' ORDER BY created DESC LIMIT 1", nativeQuery = true)
    CrawlerJob findFirstByConfigCodeWhereTestrunIsFalseandStatusIsFinished(@Param("configCode")String configCode);

}
