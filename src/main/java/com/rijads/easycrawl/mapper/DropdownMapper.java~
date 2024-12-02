package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.model.CrawlerWebsite;
import com.rijads.easycrawl.model.ProductCategory;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DropdownMapper {
    DropdownDTO crawlerWebsiteToDto(CrawlerWebsite entity);

    DropdownDTO productCategoryToDto(ProductCategory entity);

    ProductCategory dtoToProductCategory(DropdownDTO dto);
}
