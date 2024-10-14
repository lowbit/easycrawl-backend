package com.rijads.easycrawl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_category", schema = "focak")
public class ProductCategory {
    @Id
    @Column(name = "code", length=30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    public String getCode() {
        return code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
