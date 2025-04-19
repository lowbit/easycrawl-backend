package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository
        extends CrudRepository<Job, String>,
        PagingAndSortingRepository<Job, String>,
        JpaSpecificationExecutor<Job> {

    /**
     * Find the most recent job with a specific config code and job type that is finished and not a test run
     */
    @Query(value = "SELECT * FROM job WHERE config_code = :configCode AND job_type = :jobType " +
            "AND (test_run IS NULL OR test_run IS FALSE) AND status = 'Finished' " +
            "ORDER BY created DESC LIMIT 1", nativeQuery = true)
    Job findFirstByConfigCodeAndJobTypeWhereTestrunIsFalseandStatusIsFinished(
            @Param("configCode") String configCode, @Param("jobType") String jobType);

    /**
     * Find the next available job to process
     */
    @Query(value = "SELECT * FROM job j " +
            "WHERE j.status = 'Created' AND j.job_type = :jobType " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM job running " +
            "    WHERE running.website_code = j.website_code " +
            "    AND running.status = 'Running' " +
            "    AND running.job_type = :jobType" +
            ") " +
            "ORDER BY j.id ASC LIMIT 1", nativeQuery = true)
    Job findNextAvailableJob(@Param("jobType") String jobType);

    /**
     * Find jobs by type
     */
    Page<Job> findByJobType(String jobType, Pageable pageable);

    /**
     * Find pending jobs for a specific category
     */
    @Query(value = "SELECT * FROM job j " +
            "JOIN crawler_config cc ON j.config_code = cc.code " +
            "WHERE j.status = 'Created' AND j.job_type = :jobType " +
            "AND cc.category_code = :categoryCode " +
            "ORDER BY j.id ASC", nativeQuery = true)
    List<Job> findPendingJobsByCategoryAndType(
            @Param("categoryCode") String categoryCode, @Param("jobType") String jobType);
}