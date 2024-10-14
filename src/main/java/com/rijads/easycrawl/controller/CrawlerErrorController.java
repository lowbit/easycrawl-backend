package com.rijads.easycrawl.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping(value = "/api/crawler-error")
public class CrawlerErrorController {

    @GetMapping
    public String getAllCrawlErrors(Principal principal){
        return "hi "+principal.getName();
    }
}
