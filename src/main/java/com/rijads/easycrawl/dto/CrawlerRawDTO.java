package com.rijads.easycrawl.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CrawlerRawDTO {
    private Integer id;
    private String configCode;
    private String title;
    private String link;
    private String priceString;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private BigDecimal discount;
    private LocalDateTime created;
    private LocalDateTime modified;
    private Integer jobId;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public String getConfigCode() {
        return configCode;
    }

    public void setConfigCode(final String configCode) {
        this.configCode = configCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(final String link) {
        this.link = link;
    }

    public String getPriceString() {
        return priceString;
    }

    public void setPriceString(final String priceString) {
        this.priceString = priceString;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(final BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOldPrice() {
        return oldPrice;
    }

    public void setOldPrice(final BigDecimal oldPrice) {
        this.oldPrice = oldPrice;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(final BigDecimal discount) {
        this.discount = discount;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(final LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(final LocalDateTime modified) {
        this.modified = modified;
    }
}
