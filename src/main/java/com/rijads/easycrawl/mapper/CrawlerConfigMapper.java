package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.model.CrawlerConfig;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CrawlerConfigMapper {
    CrawlerConfigDTO toDto(CrawlerConfig entity);

    CrawlerConfig toEntity(CrawlerConfigDTO dto);
}
