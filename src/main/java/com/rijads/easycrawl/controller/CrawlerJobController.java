package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.CrawlerErrorDTO;
import com.rijads.easycrawl.dto.CrawlerJobDTO;
import com.rijads.easycrawl.service.CrawlerJobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crawler-job")
public class CrawlerJobController {
    private final CrawlerJobService crawlerJobService;

    public CrawlerJobController(CrawlerJobService crawlerJobService) {
        this.crawlerJobService = crawlerJobService;
    }

    @GetMapping
    public Page<CrawlerJobDTO> getAllCrawlerJobs(final Pageable page) {
        return crawlerJobService.getAllCrawlerJobs(page);
    }
    @GetMapping(value = "/{id}/errors")
    public List<CrawlerErrorDTO> getAllCrawlerJobErrors(@PathVariable Long id) {
        return crawlerJobService.getAllCrawlerJobErrors(id);
    }
    @PostMapping
    public CrawlerJobDTO create(@RequestBody CrawlerJobDTO crawlerJobDTO) {
        return crawlerJobService.create(crawlerJobDTO);
    }

}
