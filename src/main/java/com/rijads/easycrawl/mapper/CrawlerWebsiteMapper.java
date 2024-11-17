package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerWebsiteDTO;
import com.rijads.easycrawl.model.CrawlerWebsite;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CrawlerWebsiteMapper {
    CrawlerWebsiteDTO toDto(CrawlerWebsite entity);

    CrawlerWebsite dtoToEntity(CrawlerWebsiteDTO dto);
}
