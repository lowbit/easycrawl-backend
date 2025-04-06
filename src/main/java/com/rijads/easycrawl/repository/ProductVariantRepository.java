package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {
    List<ProductVariant> findByProductId(Integer productId);
    Optional<ProductVariant> findByProductAndWebsiteAndSourceUrl(Product product, CrawlerWebsite website, String sourceUrl);
    List<ProductVariant> findByRawProductId(Integer rawProductId);
}
