package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerErrorDTO;
import com.rijads.easycrawl.dto.CrawlerJobDTO;
import com.rijads.easycrawl.model.CrawlerError;
import com.rijads.easycrawl.model.CrawlerJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrawlerJobMapper {
    @Mapping(source = "crawlerWebsite.code", target = "crawlerWebsiteCode")
    @Mapping(source = "config.code", target = "crawlerConfigCode")
    CrawlerJobDTO toDto(CrawlerJob entity);

    @Mapping(source = "crawlerWebsiteCode", target = "crawlerWebsite.code")
    @Mapping(source = "crawlerConfigCode", target = "config.code")
    CrawlerJob toEntity(CrawlerJobDTO dto);

    @Mapping(source = "job.id", target = "jobId")
    CrawlerErrorDTO errorToDto(CrawlerError entity);

    @Mapping(source = "jobId", target = "job.id")
    CrawlerError errorToEntity(CrawlerErrorDTO dto);

}
