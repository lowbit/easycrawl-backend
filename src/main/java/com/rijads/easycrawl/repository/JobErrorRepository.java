package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.JobError;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface JobErrorRepository
        extends CrudRepository<JobError, String>,
        PagingAndSortingRepository<JobError, String>,
        JpaSpecificationExecutor<JobError> {

    /**
     * Find all errors for a specific job
     */
    List<JobError> findAllByJob_Id(Integer jobId);

    /**
     * Find all errors for a specific job type
     */
    List<JobError> findAllByJobType(String jobType);

    /**
     * Find all errors by source and category
     */
    List<JobError> findAllBySourceAndCategory(String source, String category);
}