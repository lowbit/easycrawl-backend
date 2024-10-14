package com.rijads.easycrawl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_config", schema = "focak")
public class CrawlerConfig {

    @Id
    @Column(length = 200)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "website_code", referencedColumnName = "code", nullable = false)
    private CrawlerWebsite crawlerWebsite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_code", referencedColumnName = "code", nullable = false)
    private ProductCategory productCategory;

    @Column(name = "start_url", nullable = false)
    private String startUrl;

    @Column(name = "all_items_sel", nullable = false)
    private String allItemsSel;

    @Column(name = "title_sel", nullable = false)
    private String titleSel;

    @Column(name = "link_sel", nullable = false)
    private String linkSel;

    @Column(name = "price_sel", nullable = false)
    private String priceSel;

    @Column(name = "use_next_page_button", nullable = false)
    private Boolean useNextPageButton;

    @Column(name = "next_page_button_sel")
    private String nextPageButtonSel;

    @Column(name = "max_pages")
    private Integer maxPages;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private LocalDateTime created;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column
    private LocalDateTime modified;

    @Column(length = 100)
    private String modifiedBy;

    public String getCode() {
        return code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public CrawlerWebsite getCrawlerWebsite() {
        return crawlerWebsite;
    }

    public void setCrawlerWebsite(final CrawlerWebsite crawlerWebsite) {
        this.crawlerWebsite = crawlerWebsite;
    }

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public void setProductCategory(final ProductCategory productCategory) {
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

    public String getLinkSel() {
        return linkSel;
    }

    public void setLinkSel(final String linkSel) {
        this.linkSel = linkSel;
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
