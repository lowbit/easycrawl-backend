package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategory(ProductCategory category);
    List<Product> findByBrand(String brand);

    /**
     * Search products by query term - matches on name, brand, or model
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.brand) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.model) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProducts(@Param("query") String query);

    /**
     * Find products by brand ordered by ID descending
     */
    List<Product> findByBrandOrderByIdDesc(String brand);

    /**
     * Find products by category ordered by ID descending
     */
    List<Product> findByCategoryOrderByIdDesc(ProductCategory category);

    /**
     * Find brands that have multiple products
     * Returns a list of [brand, count] objects
     */
    @Query("SELECT p.brand, COUNT(p) FROM Product p WHERE p.brand IS NOT NULL GROUP BY p.brand HAVING COUNT(p) > 1")
    List<Object[]> findBrandsWithMultipleProducts();

    /**
     * Find products with exact brand and model match (case insensitive)
     * Critical for preventing duplicates
     */
    List<Product> findByBrandIgnoreCaseAndModelIgnoreCase(String brand, String model);

    /**
     * Find products by brand (case insensitive)
     */
    List<Product> findByBrandIgnoreCase(String brand);

    /**
     * Find all products with non-null brand and model
     * Used for duplicate detection
     */
    List<Product> findByBrandNotNullAndModelNotNull();
}