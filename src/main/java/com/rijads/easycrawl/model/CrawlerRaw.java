package com.rijads.easycrawl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="crawler_raw", schema = "focak")
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

    @PrePersist
    protected void onCreate() {
        created = LocalDateTime.now();
        modified = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        modified = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
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
