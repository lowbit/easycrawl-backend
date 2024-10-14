package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.ProductCategory;
import com.rijads.easycrawl.repository.ProductCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository repository;

    public ProductCategoryService(ProductCategoryRepository repository){
        this.repository = repository;
    }
    public List<ProductCategory> getAllProductGategories(){
        return repository.findAll();
    }
}
