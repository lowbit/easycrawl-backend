package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerJobDto;
import com.rijads.easycrawl.model.CrawlerJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrawlerJobMapper {
    @Mapping(source = "crawlerWebsite.code", target = "crawlerWebsiteCode")
    @Mapping(source = "config.code", target = "crawlerConfigCode")
    CrawlerJobDto toDto(CrawlerJob entity);

    @Mapping(source = "crawlerWebsiteCode", target = "crawlerWebsite.code")
    @Mapping(source = "crawlerConfigCode", target = "config.code")
    CrawlerJob toEntity(CrawlerJobDto dto);}
