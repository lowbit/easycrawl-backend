package com.rijads.easycrawl.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "crawler_raw",
        schema = "public",
        indexes = {@Index(name = "idx_crawler_raw_processed", columnList = "processed")})
public class CrawlerRaw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_code", nullable = false, length = 200)
    private String configCode;

    @Column(length = 255)
    private String title;

    @Column(length = 255)
    private String link;

    @Column(name = "price_string", length = 60)
    private String priceString;

    @Column(precision = 38, scale = 2)
    private BigDecimal price;

    @Column(name = "oldprice", precision = 38, scale = 2)
    private BigDecimal oldPrice;

    @Column(precision = 38, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @Column(nullable = false)
    private LocalDateTime modified;

    @Column(name = "processed")
    private Boolean processed = false;

    @Column(name = "matched_product_id")
    private Integer matchedProductId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, name = "job_id")
    private Job job;

    @PrePersist
    protected void onCreate() {
        created = LocalDateTime.now();
        modified = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        modified = LocalDateTime.now();
    }

    public Boolean getProcessed() {
        return processed;
    }

    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }

    public Integer getMatchedProductId() {
        return matchedProductId;
    }

    public void setMatchedProductId(Integer matchedProductId) {
        this.matchedProductId = matchedProductId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
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
