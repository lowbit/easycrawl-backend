package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.UnmappableItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnmappableItemRepository extends JpaRepository<UnmappableItem, Integer> {

    /**
     * Find unmappable items by reason code
     */
    List<UnmappableItem> findByReasonCode(UnmappableItem.ReasonCode reasonCode);

    /**
     * Find unmappable items by category
     */
    List<UnmappableItem> findByCategory(String category);

    /**
     * Search unmappable items by title containing the search term
     */
    List<UnmappableItem> findByTitleContainingIgnoreCase(String searchTerm);

    /**
     * Find items that have been attempted less than the specified number of times
     */
    List<UnmappableItem> findByAttemptsLessThan(int maxAttempts);

    /**
     * Find unmappable items with title containing the search term and by reason code
     */
    List<UnmappableItem> findByTitleContainingIgnoreCaseAndReasonCode(String searchTerm, UnmappableItem.ReasonCode reasonCode);

    /**
     * Custom query to find the most common reasons for unmappable items
     */
    @Query("SELECT u.reasonCode, COUNT(u) FROM UnmappableItem u GROUP BY u.reasonCode ORDER BY COUNT(u) DESC")
    List<Object[]> findMostCommonReasons();

    /**
     * Custom query to find the most common categories with unmappable items
     */
    @Query("SELECT u.category, COUNT(u) FROM UnmappableItem u WHERE u.category IS NOT NULL GROUP BY u.category ORDER BY COUNT(u) DESC")
    List<Object[]> findMostProblematicCategories();

    /**
     * Find items with specific words in title that could be potential brands
     */
    @Query("SELECT u FROM UnmappableItem u WHERE u.reasonCode = 'MISSING_BRAND' AND LOWER(u.title) LIKE LOWER(CONCAT('%', :word, '%'))")
    List<UnmappableItem> findPotentialMissingBrands(@Param("word") String word);

    /**
     * Find unmappable items by raw item ID in a given list
     */
    List<UnmappableItem> findByRawItemIdIn(List<Integer> rawItemIds);
}