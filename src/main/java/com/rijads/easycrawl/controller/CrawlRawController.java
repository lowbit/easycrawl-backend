package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.service.CrawlerRawService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping(value = "/crawler-raw")
public class CrawlRawController {
    private final CrawlerRawService service;

    public CrawlRawController(CrawlerRawService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CrawlerRaw> getAllCrawlRaws(
            @RequestParam(required = false) String configCode,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime createdTo,
            Pageable pageable) {
        return service.getAllCrawlerRaws(
                configCode, title, minPrice, maxPrice, createdFrom, createdTo, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CrawlerRaw> getCrawlerRawById(@PathVariable Integer id) {
        return service.getCrawlerRawById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public CrawlerRaw createCrawlerRaw(@RequestBody CrawlerRaw crawlerRaw) {
        return service.saveCrawlerRaw(crawlerRaw);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CrawlerRaw> updateCrawlerRaw(
            @PathVariable Integer id, @RequestBody CrawlerRaw crawlerRaw) {
        return service.getCrawlerRawById(id)
                .map(
                        existingCrawlerRaw -> {
                            crawlerRaw.setId(id);
                            return ResponseEntity.ok(service.saveCrawlerRaw(crawlerRaw));
                        })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrawlerRaw(@PathVariable Integer id) {
        service.deleteCrawlerRaw(id);
        return ResponseEntity.noContent().build();
    }
}
