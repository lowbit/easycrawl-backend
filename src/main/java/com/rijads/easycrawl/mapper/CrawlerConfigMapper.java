package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.model.CrawlerConfig;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrawlerConfigMapper {
    @Mapping(source = "crawlerWebsite.code", target = "crawlerWebsite")
    @Mapping(source = "productCategory.code", target = "productCategory")
    CrawlerConfigDTO toDto(CrawlerConfig entity);

    @Mapping(source = "crawlerWebsite", target = "crawlerWebsite.code")
    @Mapping(source = "productCategory", target = "productCategory.code")
    CrawlerConfig toEntity(CrawlerConfigDTO dto);
}
