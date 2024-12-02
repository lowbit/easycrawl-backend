package com.rijads.easycrawl.dto;

import java.time.LocalDateTime;

public class CrawlerJobDTO {
    private Integer id;
    private String crawlerWebsiteCode;
    private String crawlerConfigCode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime created;
    private String createdBy;
    private LocalDateTime modified;
    private String modifiedBy;
    private Boolean testRun;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(final LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(final LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
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

    public String getCrawlerWebsiteCode() {
        return crawlerWebsiteCode;
    }

    public void setCrawlerWebsiteCode(String crawlerWebsiteCode) {
        this.crawlerWebsiteCode = crawlerWebsiteCode;
    }

    public String getCrawlerConfigCode() {
        return crawlerConfigCode;
    }

    public void setCrawlerConfigCode(String crawlerConfigCode) {
        this.crawlerConfigCode = crawlerConfigCode;
    }

    public Boolean getTestRun() {
        return testRun;
    }

    public void setTestRun(Boolean testRun) {
        this.testRun = testRun;
    }
}
