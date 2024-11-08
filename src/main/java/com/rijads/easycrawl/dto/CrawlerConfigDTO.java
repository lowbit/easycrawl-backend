package com.rijads.easycrawl.dto;

import java.time.LocalDateTime;

public class CrawlerConfigDTO {
    private String code;
    private CrawlerWebsiteDTO crawlerWebsite;
    private ProductCategoryDTO productCategory;
    private String startUrl;
    private String allItemsSel;
    private String titleSel;
    private String linkSel;
    private String priceSel;
    private Boolean useNextPageButton;
    private String nextPageButtonSel;
    private Integer maxPages;
    private Boolean active;
    private LocalDateTime created;
    private String createdBy;
    private LocalDateTime modified;
    private String modifiedBy;

    public String getLinkSel() {
        return linkSel;
    }

    public void setLinkSel(final String linkSel) {
        this.linkSel = linkSel;
    }

    public String getCode() {
        return code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public CrawlerWebsiteDTO getCrawlerWebsite() {
        return crawlerWebsite;
    }

    public void setCrawlerWebsite(final CrawlerWebsiteDTO crawlerWebsite) {
        this.crawlerWebsite = crawlerWebsite;
    }

    public ProductCategoryDTO getProductCategory() {
        return productCategory;
    }

    public void setProductCategory(final ProductCategoryDTO productCategory) {
        this.productCategory = productCategory;
    }

    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(final String startUrl) {
        this.startUrl = startUrl;
    }

    public String getAllItemsSel() {
        return allItemsSel;
    }

    public void setAllItemsSel(final String allItemsSel) {
        this.allItemsSel = allItemsSel;
    }

    public String getTitleSel() {
        return titleSel;
    }

    public void setTitleSel(final String titleSel) {
        this.titleSel = titleSel;
    }

    public String getPriceSel() {
        return priceSel;
    }

    public void setPriceSel(final String priceSel) {
        this.priceSel = priceSel;
    }

    public Boolean getUseNextPageButton() {
        return useNextPageButton;
    }

    public void setUseNextPageButton(final Boolean useNextPageButton) {
        this.useNextPageButton = useNextPageButton;
    }

    public String getNextPageButtonSel() {
        return nextPageButtonSel;
    }

    public void setNextPageButtonSel(final String nextPageButtonSel) {
        this.nextPageButtonSel = nextPageButtonSel;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(final Integer maxPages) {
        this.maxPages = maxPages;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(final LocalDateTime created) {
        this.created = created;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(final LocalDateTime modified) {
        this.modified = modified;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(final String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}
