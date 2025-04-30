package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.service.CrawlerConfigService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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


    @GetMapping(value = "/crawler-config-dropdown")
    public List<DropdownDTO> getAllCrawlerConfigsByWebsiteCodeDropdown( @RequestParam final String websiteCode) {
        return service.getAllCrawlerConfigsDropdown(websiteCode);
    }

    @PostMapping()
    public ResponseEntity<CrawlerConfigDTO> addCrawlerConfig(
            @RequestBody CrawlerConfigDTO request) {
        return service.addCrawlerConfig(request);
    }

    @PutMapping
    public ResponseEntity<CrawlerConfigDTO> editCrawlerConfig(
            @RequestParam final String code, @RequestBody CrawlerConfigDTO request) {
        return service.editCrawlerConfig(code, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCrawlerConfig(@RequestParam final String code) {
        return service.deleteCrawlerConfig(code);
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteCrawlerConfigs(@RequestBody final List<String> codes) {
        return service.deleteCrawlerConfigs(codes);
    }
}
