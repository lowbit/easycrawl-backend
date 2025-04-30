package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.service.RegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping(value = "/registry")
public class RegistryController {
    public final RegistryService service;

    public RegistryController(RegistryService service){
        this.service = service;
    }
    @GetMapping(value = "/crawler-website-dropdown")
    public List<DropdownDTO> getAllWebsitesDropdown() {
        return service.getAllWebsitesDropdown();
    }

    @GetMapping(value = "/product-category-dropdown")
    public List<DropdownDTO> getAllCategoriesDropdown() {
        return service.getAllCategoriesDropdown();
    }

    @PostMapping(value = "/product-category")
    public ResponseEntity<DropdownDTO> addProductCategory(@RequestBody DropdownDTO request) {
        return service.addProductCategory(request);
    }
    @DeleteMapping(value = "/product-category/{id}")
    public ResponseEntity<Void> deleteProductCategory(@PathVariable String id) {
        return service.deleteProductCategory(id);
    }

    @PostMapping(value = "/crawler-website")
    public ResponseEntity<CrawlerWebsiteDTO> addCrawlerWebsite(@RequestBody CrawlerWebsiteDTO request) {
        return service.addCrawlerWebsite(request);
    }
    @DeleteMapping(value = "/crawler-website/{id}")
    public ResponseEntity<Void> deleteCrawlerWebsite(@PathVariable String id){
        return service.deleteCrawlerWebsite(id);
    }
}
