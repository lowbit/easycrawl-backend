package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends CrudRepository<ProductVariant,Integer>,
        PagingAndSortingRepository<ProductVariant, Integer>, JpaSpecificationExecutor<ProductVariant> {
    /**
     * Find variants by product ID
     */
    List<ProductVariant> findByProductId(Integer productId);

    /**
     * Find variant by product and source URL
     */
    Optional<ProductVariant> findByProductAndSourceUrl(Product product, String sourceUrl);

    /**
     * Find variants by raw product ID
     */
    List<ProductVariant> findByRawProductId(Integer rawProductId);

    /**
     * Find variants by title containing search term
     */
    List<ProductVariant> findByTitleContaining(String title);

    /**
     * Find all raw product IDs that have associated variants
     */
    @Query("SELECT DISTINCT pv.rawProductId FROM ProductVariant pv WHERE pv.rawProductId IS NOT NULL")
    List<Integer> findRawProductIdsWithVariants();

    /**
     * Count variants by product ID
     */
    long countByProductId(Integer productId);

    /**
     * Find all variants for a specific product
     */
    List<ProductVariant> findByProduct(Product product);

    /**
     * Delete all variants for a specific product
     */
    void deleteByProduct(Product product);

    /**
     * Find variants with the same title and website code
     */
    List<ProductVariant> findByTitleAndWebsiteCode(String title, String websiteCode);

    /**
     * Get unique title and website combinations for a product
     */
    @Query("SELECT DISTINCT new map(pv.title as title, pv.website.code as websiteCode) FROM ProductVariant pv WHERE pv.product.id = :productId")
    List<java.util.Map<String, String>> findDistinctTitleAndWebsiteByProductId(@Param("productId") Integer productId);

    /**
     * Get unique website codes for a product
     */
    @Query("SELECT DISTINCT pv.website.code FROM ProductVariant pv WHERE pv.product.id = :productId")
    List<String> findDistinctWebsiteCodesByProductId(@Param("productId") Integer productId);

    /**
     * Get unique titles for a product
     */
    @Query("SELECT DISTINCT pv.title FROM ProductVariant pv WHERE pv.product.id = :productId")
    List<String> findDistinctTitlesByProductId(@Param("productId") Integer productId);
}