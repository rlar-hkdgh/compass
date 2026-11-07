package com.example.compass.recruit.entity;

import jakarta.persistence.*;

/** Loaded explicitly; listing queries never traverse this separate record. */
@Entity
@Table(name="job_posting_collection_metadata")
public class JobPostingCollectionMetadata {
    @EmbeddedId private PostingId id;
    @Column(name="source_metadata", columnDefinition="text")
    private String sourceMetadata;
    protected JobPostingCollectionMetadata() {}
    public JobPostingCollectionMetadata(PostingId id) { this.id=id; }
    public PostingId getId() { return id; }
    public String getSourceMetadata() { return sourceMetadata; }
    public void setSourceMetadata(String value) { this.sourceMetadata=value; }
}
