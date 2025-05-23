package com.rijads.easycrawl.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name="product_registry", schema = "public",
uniqueConstraints = @UniqueConstraint(columnNames = {"registry_type", "registry_key"}))
public class ProductRegistry {
    public enum RegistryType {
        BRAND,
        NOT_BRAND,
        COMMON_WORD,        // Common words to ignore
        COLOR,
        STORAGE_PATTERN     // Patterns for storage capacity
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "registry_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RegistryType registryType;

    @Column(name = "registry_key", nullable = false, length = 100)
    private String registryKey;

    @Column(name = "registry_value", length = 255)
    private String registryValue;

    @Column(name = "description")
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created", nullable = false)
    private LocalDateTime created;

    @Column(name = "modified")
    private LocalDateTime modified;

    @PrePersist
    protected void onCreate() {
        created = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        modified = LocalDateTime.now();
    }

    // Getters and setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public RegistryType getRegistryType() {
        return registryType;
    }

    public void setRegistryType(RegistryType registryType) {
        this.registryType = registryType;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }
}
