package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.service.ProductCategoryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/product-category")
public class ProductCategoryController {
    private final ProductCategoryService service;

    public ProductCategoryController(ProductCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductCategory> getAllProductCategories() {
        return service.getAllProductGategories();
    }
}
