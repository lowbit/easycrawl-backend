package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerRawDTO;
import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.model.CrawlerWebsite;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrawlerRawMapper {
    @Mapping(source = "job.id", target = "jobId")
    CrawlerRawDTO toDto(CrawlerRaw entity);

    @Mapping(source = "jobId", target = "job.id")
    CrawlerRaw dtoToEntity(CrawlerRawDTO dto);
}
