package com.rijads.easycrawl.dto;

import java.util.List;

/**
 * Request class for adding brands
 */
public class AddBrandsRequest {
    private List<String> brands;
    private String description;

    public List<String> getBrands() {
        return brands;
    }

    public void setBrands(List<String> brands) {
        this.brands = brands;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
