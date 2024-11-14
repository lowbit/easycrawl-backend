package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.CrawlerWebsiteDropdownDTO;
import com.rijads.easycrawl.model.CrawlerWebsite;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CrawlerWebsiteMapper {
    CrawlerWebsiteDropdownDTO toDropdownDto(CrawlerWebsite entity);
}
