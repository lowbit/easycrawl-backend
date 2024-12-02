package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.dto.CrawlerErrorDTO;
import com.rijads.easycrawl.model.CrawlerError;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface CrawlerErrorRepository
        extends CrudRepository<CrawlerError, String>,
                PagingAndSortingRepository<CrawlerError, String>,
                JpaSpecificationExecutor<CrawlerError> {

    List<CrawlerError> findAllByJob_Id(Integer jobId);
}
