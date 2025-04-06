package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.ProductDTO;
import com.rijads.easycrawl.dto.ProductDetailDTO;
import com.rijads.easycrawl.dto.ProductVariantDTO;
import com.rijads.easycrawl.model.Product;
import com.rijads.easycrawl.model.ProductVariant;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "lowestPrice", ignore = true)
    @Mapping(target = "highestPrice", ignore = true)
    @Mapping(target = "storeCount", ignore = true)
    @Mapping(target = "category", source = "category.name")
    ProductDTO toDto(Product product);

    @AfterMapping
    default void setAggregatedValues(Product product, @MappingTarget ProductDTO productDTO) {
        if(product.getVariants() != null && !product.getVariants().isEmpty()) {
            productDTO.setLowestPrice(product.getVariants().stream()
                    .filter(v-> v.getPrice() != null)
                    .map(ProductVariant::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(null));
            productDTO.setHighestPrice(product.getVariants().stream()
                    .filter(v-> v.getPrice()!=null)
                    .map(ProductVariant::getPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(null));
            productDTO.setStoreCount((int) product.getVariants().stream()
                    .map(v->v.getWebsite().getCode())
                    .distinct()
                    .count());
        }
    }

    @Mapping(target = "category.code", source = "category")
    Product dtoToEntity(ProductDTO productDTO);

    //@Mapping(target = "variants", source = "variants")
    @Mapping(target = "category", source = "product.category.name")
    ProductDetailDTO productAndVariantsToProductDetailDTO(Product product, List<ProductVariant> variants);

    @Mapping(target = "websiteName", source = "website.name")
    @Mapping(target = "websiteCode", source = "website.code")
    ProductVariantDTO variantToVariantDTO(ProductVariant variant);

    List<ProductVariantDTO> variantsToVariantDTOs(List<ProductVariant> variants);

    default List<ProductVariantDTO> variantSetToVariantDTOs(Set<ProductVariant> variants) {
        if (variants == null) {
            return null;
        }
        return variants.stream()
                .map(this::variantToVariantDTO)
                .toList();
    }

}
