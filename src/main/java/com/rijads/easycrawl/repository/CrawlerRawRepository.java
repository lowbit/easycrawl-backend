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
    List<CrawlerRaw> findByProcessedFalse();
    List<CrawlerRaw> findByProcessedFalseAndConfigCodeContaining(String categoryCode);
    List<CrawlerRaw> findTop20ByProcessedTrueAndConfigCodeContainingOrderByIdDesc(String categoryCode);
    
    /**
     * Find unprocessed items grouped by title to avoid processing the same title multiple times
     * We select one raw item for each unique title
     */
    @Query("SELECT cr FROM CrawlerRaw cr WHERE (cr.processed IS NULL OR cr.processed = false) " +
           "AND cr.id IN (SELECT MIN(c.id) FROM CrawlerRaw c WHERE c.processed IS NULL OR c.processed = false " +
           "GROUP BY c.title)")
    List<CrawlerRaw> findUnprocessedItemsGroupedByTitle();
    
    /**
     * Find unprocessed items for a specific category, grouped by title
     */
    @Query("SELECT cr FROM CrawlerRaw cr WHERE (cr.processed IS NULL OR cr.processed = false) " +
           "AND cr.configCode LIKE %:categoryCode% " +
           "AND cr.id IN (SELECT MIN(c.id) FROM CrawlerRaw c WHERE (c.processed IS NULL OR c.processed = false) " +
           "AND c.configCode LIKE %:categoryCode% GROUP BY c.title)")
    List<CrawlerRaw> findUnprocessedItemsGroupedByTitleForCategory(@Param("categoryCode") String categoryCode);
    
    /**
     * Update matched product ID for raw items
     */
    @Modifying
    @Query("UPDATE CrawlerRaw r SET r.matchedProductId = :newProductId WHERE r.matchedProductId = :oldProductId")
    void updateMatchedProductId(@Param("oldProductId") Integer oldProductId, @Param("newProductId") Integer newProductId);
    
    /**
     * Find all crawler raw items that are mapped to a specific product ID
     */
    List<CrawlerRaw> findByMatchedProductId(Integer matchedProductId);
    
    /**
     * Find all crawler raw items with a specific title
     */
    List<CrawlerRaw> findByTitle(String title);
    
    /**
     * Find all crawler raw items with title exactly matching the given title
     * This is case sensitive and exact
     */
    @Query("SELECT cr FROM CrawlerRaw cr WHERE cr.title = :title")
    List<CrawlerRaw> findByExactTitle(@Param("title") String title);
    
    /**
     * Find all crawler raw items with same processed status for a specific title
     */
    @Query("SELECT cr FROM CrawlerRaw cr WHERE cr.title = :title AND (cr.processed IS NULL OR cr.processed = :processed)")
    List<CrawlerRaw> findByTitleAndProcessedStatus(@Param("title") String title, @Param("processed") Boolean processed);
    
    /**
     * Bulk update crawler raw items to be unprocessed by list of title
     */
    @Modifying
    @Query("UPDATE CrawlerRaw cr SET cr.processed = false, cr.matchedProductId = NULL WHERE cr.title IN :titles")
    int bulkResetByTitles(@Param("titles") List<String> titles);
    
    /**
     * Find all unprocessed items (where processed is null or false) for a category
     */
    @Query("SELECT cr FROM CrawlerRaw cr WHERE (cr.processed IS NULL OR cr.processed = false) " +
           "AND cr.configCode LIKE %:categoryCode%")
    List<CrawlerRaw> findByProcessedNullOrFalseAndConfigCodeContaining(@Param("categoryCode") String categoryCode);
}
