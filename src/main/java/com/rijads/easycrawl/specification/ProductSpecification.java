package com.rijads.easycrawl.specification;

import com.rijads.easycrawl.model.Product;
import org.springframework.data.jpa.domain.Specification;

public class ProductSpecification {
    public static Specification<Product> hasName(String name){
        return ((root, query, criteriaBuilder) ->
                name==null?null:criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
    }
    public static Specification<Product> hasCategory(String category){
        return ((root, query, criteriaBuilder) ->
                category==null?null:criteriaBuilder.equal(root.get("category").get("name"), category));
    }
    public static Specification<Product> hasBrand(String brand){
        return ((root, query, criteriaBuilder) ->
                brand==null?null:criteriaBuilder.equal(root.get("brand"), brand));
    }
}
