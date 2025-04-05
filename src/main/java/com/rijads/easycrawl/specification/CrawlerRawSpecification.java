package com.rijads.easycrawl.specification;

import com.rijads.easycrawl.model.CrawlerRaw;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class CrawlerRawSpecification {
    public static Specification<CrawlerRaw> hasConfigCode(String configCode) {
        return (root, query, criteriaBuilder) ->
                configCode == null
                        ? null
                        : criteriaBuilder.equal(root.get("configCode"), configCode);
    }

    public static Specification<CrawlerRaw> titleContains(String title) {
        return (root, query, criteriaBuilder) ->
                title == null
                        ? null
                        : criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("title")),
                                "%" + title.toLowerCase() + "%");
    }

    public static Specification<CrawlerRaw> priceBetween(Double minPrice, Double maxPrice) {
        return (root, query, criteriaBuilder) -> {
            if (minPrice == null && maxPrice == null) return null;
            if (minPrice == null)
                return criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
            if (maxPrice == null)
                return criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
            return criteriaBuilder.between(root.get("price"), minPrice, maxPrice);
        };
    }

    public static Specification<CrawlerRaw> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, criteriaBuilder) -> {
            if (from == null && to == null) return null;
            if (from == null) return criteriaBuilder.lessThanOrEqualTo(root.get("created"), to);
            if (to == null) return criteriaBuilder.greaterThanOrEqualTo(root.get("created"), from);
            return criteriaBuilder.between(root.get("created"), from, to);
        };
    }
    public static Specification<CrawlerRaw> jobId(Double jobId) {
        return (root, query, criteriaBuilder) -> {
            if (jobId == null) return null;
            return criteriaBuilder.equal(root.get("job").get("id"), jobId);
        };
    }
}
