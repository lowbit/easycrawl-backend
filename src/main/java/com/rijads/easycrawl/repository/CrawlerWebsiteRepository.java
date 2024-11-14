package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.CrawlerWebsite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerWebsiteRepository extends JpaRepository<CrawlerWebsite, String> {}
