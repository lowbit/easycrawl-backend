package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.PriceHistoryDTO;
import com.rijads.easycrawl.model.PriceHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriceHistoryMapper {
    
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "websiteCode", source = "website.code")
    @Mapping(target = "websiteName", source = "website.name")
    PriceHistoryDTO toDto(PriceHistory priceHistory);
    
    List<PriceHistoryDTO> toDtoList(List<PriceHistory> priceHistories);
} 