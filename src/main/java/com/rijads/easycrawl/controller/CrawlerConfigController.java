package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.service.CrawlerConfigService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(value = "/crawler-config")
public class CrawlerConfigController {
    public final CrawlerConfigService service;

    public CrawlerConfigController(final CrawlerConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CrawlerConfigDTO> getAllCrawlerConfigs(
            @RequestParam(required = false) final String website,
            @RequestParam(required = false) final String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final LocalDateTime createdTo,
            final Pageable pageable) {
        return service.getAllCrawlerConfigs(website, category, createdFrom, createdTo, pageable);
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
    public ResponseEntity<DropdownDTO> addCategory(@RequestBody DropdownDTO request) {
        return service.addProductCategory(request);
    }

    @PostMapping(value = "/crawler-website")
    public ResponseEntity<CrawlerWebsiteDTO> addCategory(@RequestBody CrawlerWebsiteDTO request) {
        return service.addCrawlerWebsite(request);
    }

    @PostMapping()
    public ResponseEntity<CrawlerConfigDTO> addCrawlerConfig(
            @RequestBody CrawlerConfigDTO request) {
        return service.addCrawlerConfig(request);
    }
}
