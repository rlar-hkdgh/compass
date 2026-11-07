package com.example.compass.recruit.entity;

import jakarta.persistence.*;

/** Loaded explicitly; listing queries never traverse this separate record. */
@Entity
@Table(name="job_posting_detail")
public class JobPostingDetail {
    @EmbeddedId private PostingId id;
    @Column(name="source_url", nullable=false, length=2000)
    private String sourceUrl;
    @Column(columnDefinition="text")
    private String keywords;
    @Column(columnDefinition="text")
    private String description;
    protected JobPostingDetail() {}
    public JobPostingDetail(PostingId id) { this.id=id; }
    public PostingId getId() { return id; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl=value; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String value) { this.keywords=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
}
