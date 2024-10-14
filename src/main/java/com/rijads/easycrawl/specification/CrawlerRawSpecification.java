package com.rijads.easycrawl.specification;

import com.rijads.easycrawl.model.CrawlerRaw;
import org.springframework.data.jpa.domain.Specification;

public class CrawlerRawSpecification {
    public static Specification<CrawlerRaw> hasConfigCode(String configCode){
        return (root,query,criteriaBuilder) ->
                configCode == null ? null: criteriaBuilder.equal(root.get("configCode"),configCode);
    }
    public static Specification<CrawlerRaw> titleContains(String title) {
        return (root,query,criteriaBuilder) ->
                title == null ? null: criteriaBuilder.like(criteriaBuilder.lower(root.get("title")),"%"+title.toLowerCase()+"%");
    }
    public static Specification<CrawlerRaw>priceBetween(Double minPrice, Double maxPrice) {
        return (root,query,criteriaBuilder) -> {
                if(minPrice == null && maxPrice == null) return null;
                if(minPrice == null) return criteriaBuilder.lessThanOrEqualTo(root.get("price"),maxPrice);
                if(maxPrice == null) return criteriaBuilder.greaterThanOrEqualTo(root.get("price"),minPrice);
                return criteriaBuilder.between(root.get("price"),minPrice,maxPrice);
                };
    }
}
