package com.example.compass.recruit.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class PostingId implements Serializable {
    @Column(name="source_code",length=32) private String sourceCode;
    @Column(name="source_posting_id",length=64) private String sourcePostingId;
    protected PostingId() {}
    public PostingId(String sourceCode,String sourcePostingId) { this.sourceCode=sourceCode; this.sourcePostingId=sourcePostingId; }
    public String getSourceCode() { return sourceCode; }
    public String getSourcePostingId() { return sourcePostingId; }
    @Override public boolean equals(Object o) { return o instanceof PostingId p && Objects.equals(sourceCode,p.sourceCode) && Objects.equals(sourcePostingId,p.sourcePostingId); }
    @Override public int hashCode() { return Objects.hash(sourceCode,sourcePostingId); }
}
