package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {
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
}