package com.rijads.easycrawl.specification;

import com.rijads.easycrawl.model.CrawlerConfig;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class CrawlerConfigSpecification {
    public static Specification<CrawlerConfig> hasWebsite(final String website) {
        return ((root, query, criteriaBuilder) ->
                website == null
                        ? null
                        : criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("crawlerWebsite").get("code")),
                                '%' + website.toLowerCase() + '%'));
    }

    public static Specification<CrawlerConfig> hasCategory(final String category) {
        return ((root, query, criteriaBuilder) ->
                category == null
                        ? null
                        : criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("productCategory").get("code")),
                                '%' + category.toLowerCase() + '%'));
    }

    public static Specification<CrawlerConfig> createdBetween(
            final LocalDateTime from, final LocalDateTime to) {
        return (root, query, criteriaBuilder) -> {
            if (from == null && to == null) return null;
            if (from == null) return criteriaBuilder.lessThanOrEqualTo(root.get("created"), to);
            if (to == null) return criteriaBuilder.greaterThanOrEqualTo(root.get("created"), from);
            return criteriaBuilder.between(root.get("created"), from, to);
        };
    }
}
