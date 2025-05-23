package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.PriceHistory;
import com.rijads.easycrawl.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Integer>, 
        JpaSpecificationExecutor<PriceHistory> {

    /**
     * Find price history for a specific variant
     */
    List<PriceHistory> findByVariantOrderByRecordedAtDesc(ProductVariant variant);
    
    /**
     * Find price history for a variant within a date range
     */
    List<PriceHistory> findByVariantAndRecordedAtBetweenOrderByRecordedAtDesc(
            ProductVariant variant, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find price history for multiple variants
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant.id IN :variantIds ORDER BY ph.recordedAt DESC")
    List<PriceHistory> findByVariantIdsOrderByRecordedAtDesc(@Param("variantIds") List<Integer> variantIds);
    
    /**
     * Find price history for variants of a product
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant.product.id = :productId ORDER BY ph.recordedAt DESC")
    List<PriceHistory> findByProductIdOrderByRecordedAtDesc(@Param("productId") Integer productId);
    
    /**
     * Find price history for variants with the same title and website
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant.title = :title AND ph.website.code = :websiteCode ORDER BY ph.recordedAt DESC")
    List<PriceHistory> findByTitleAndWebsiteCodeOrderByRecordedAtDesc(
            @Param("title") String title, @Param("websiteCode") String websiteCode);
            
    /**
     * Delete all price history for a variant
     */
    void deleteByVariantId(Integer variantId);
    
    /**
     * Delete all price history for a product
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PriceHistory ph WHERE ph.variant.product.id = :productId")
    void deleteByProductId(@Param("productId") Integer productId);

    /**
     * Find recent price history for a variant with limit
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant = :variant ORDER BY ph.recordedAt DESC LIMIT :limit")
    List<PriceHistory> findRecentByVariantWithLimit(
            @Param("variant") ProductVariant variant, @Param("limit") int limit);
            
    /**
     * Find price history for a variant with limit and with distinct dates
     * This gets one entry per day for the display
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant.id = :variantId " +
           "AND ph.recordedAt IN (" +
           "SELECT MAX(ph2.recordedAt) FROM PriceHistory ph2 " + 
           "WHERE ph2.variant.id = :variantId " +
           "GROUP BY FUNCTION('DATE', ph2.recordedAt)) " +
           "ORDER BY ph.recordedAt DESC")
    List<PriceHistory> findDailyByVariantId(@Param("variantId") Integer variantId);
    
    /**
     * Find price history for multiple variants with distinct dates
     * This gets one entry per day per variant for display purposes
     */
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.variant.id IN :variantIds " +
           "AND (ph.variant.id, ph.recordedAt) IN (" +
           "SELECT ph2.variant.id, MAX(ph2.recordedAt) FROM PriceHistory ph2 " + 
           "WHERE ph2.variant.id IN :variantIds " +
           "GROUP BY ph2.variant.id, FUNCTION('DATE', ph2.recordedAt)) " +
           "ORDER BY ph.recordedAt DESC")
    List<PriceHistory> findDailyByVariantIds(@Param("variantIds") List<Integer> variantIds);
} 