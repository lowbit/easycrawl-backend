package com.rijads.easycrawl.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to track items that couldn't be mapped to products and the reasons why.
 * This provides visibility into mapping issues for later debugging and improvement.
 */
@Entity
@Table(
        name = "unmappable_item",
        schema = "public",
        indexes = {
                @Index(name = "idx_unmappable_item_raw_id", columnList = "raw_item_id"),
                @Index(name = "idx_unmappable_item_reason_code", columnList = "reason_code"),
                @Index(name = "idx_unmappable_item_category", columnList = "category")
        }
)
public class UnmappableItem {

    public enum ReasonCode {
        MISSING_BRAND,           // No brand could be identified from registry
        INSUFFICIENT_SIMILARITY, // Similarity below threshold
        INVALID_DATA,            // Data issues like missing required fields
        INVALID_CATEGORY,        // Category couldn't be matched
        NO_SIMILAR_ITEMS,        // No similar items found
        OTHER                    // Other reasons (see details in reason field)
    }

    @Id
    @Column(name = "raw_item_id")
    private Integer rawItemId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "config_code", length = 255)
    private String configCode;

    @Column(name = "reason_code", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ReasonCode reasonCode;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "extracted_data", columnDefinition = "TEXT")
    private String extractedData;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 1;

    @Column(name = "last_attempt", nullable = false)
    private LocalDateTime lastAttempt;

    @Column(name = "first_seen", nullable = false)
    private LocalDateTime firstSeen;

    @PrePersist
    protected void onCreate() {
        if (firstSeen == null) {
            firstSeen = LocalDateTime.now();
        }
        lastAttempt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastAttempt = LocalDateTime.now();
        attempts++;
    }

    public Integer getRawItemId() {
        return rawItemId;
    }

    public void setRawItemId(Integer rawItemId) {
        this.rawItemId = rawItemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getConfigCode() {
        return configCode;
    }

    public void setConfigCode(String configCode) {
        this.configCode = configCode;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(String extractedData) {
        this.extractedData = extractedData;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(LocalDateTime lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }
}