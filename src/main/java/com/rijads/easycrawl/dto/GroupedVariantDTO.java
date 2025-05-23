package com.rijads.easycrawl.dto;

import java.math.BigDecimal;
import java.util.List;

public class GroupedVariantDTO {
    private String title;
    private String websiteName;
    private String websiteCode;
    private String color;
    private String size;
    private BigDecimal currentPrice;
    private BigDecimal lowestPrice;
    private BigDecimal highestPrice;
    private String sourceUrl;
    private String imageUrl;
    private List<PriceHistoryDTO> priceHistory;
    
    // Additional metadata
    private Integer variantCount;
    private String lastUpdated;

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWebsiteName() {
        return websiteName;
    }

    public void setWebsiteName(String websiteName) {
        this.websiteName = websiteName;
    }

    public String getWebsiteCode() {
        return websiteCode;
    }

    public void setWebsiteCode(String websiteCode) {
        this.websiteCode = websiteCode;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getLowestPrice() {
        return lowestPrice;
    }

    public void setLowestPrice(BigDecimal lowestPrice) {
        this.lowestPrice = lowestPrice;
    }

    public BigDecimal getHighestPrice() {
        return highestPrice;
    }

    public void setHighestPrice(BigDecimal highestPrice) {
        this.highestPrice = highestPrice;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<PriceHistoryDTO> getPriceHistory() {
        return priceHistory;
    }

    public void setPriceHistory(List<PriceHistoryDTO> priceHistory) {
        this.priceHistory = priceHistory;
    }

    public Integer getVariantCount() {
        return variantCount;
    }

    public void setVariantCount(Integer variantCount) {
        this.variantCount = variantCount;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 