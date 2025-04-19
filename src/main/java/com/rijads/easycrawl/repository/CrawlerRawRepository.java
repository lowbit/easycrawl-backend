package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerRaw;

import com.rijads.easycrawl.model.Job;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlerRawRepository
        extends CrudRepository<CrawlerRaw, Integer>,
                PagingAndSortingRepository<CrawlerRaw, Integer>,
                JpaSpecificationExecutor<CrawlerRaw> {
    List<CrawlerRaw> getByJob(Job job);
    List<CrawlerRaw> findByProcessedNullOrProcessedFalse();
    List<CrawlerRaw> findByProcessedFalseAndConfigCodeContaining(String categoryCode);
    List<CrawlerRaw> findTop20ByProcessedTrueAndConfigCodeContainingOrderByIdDesc(String categoryCode);
    /**
     * Update matched product ID for raw items
     */
    @Modifying
    @Query("UPDATE CrawlerRaw r SET r.matchedProductId = :newProductId WHERE r.matchedProductId = :oldProductId")
    void updateMatchedProductId(@Param("oldProductId") Integer oldProductId, @Param("newProductId") Integer newProductId);
}
