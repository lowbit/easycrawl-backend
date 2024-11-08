package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.service.CrawlerConfigService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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
}
