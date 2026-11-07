package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="posting_analysis")
public class PostingAnalysis {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="source_code", nullable=false, length=32)
    private String sourceCode;
    @Column(name="source_posting_id", nullable=false, length=64)
    private String sourcePostingId;
    @Column(name="input_hash", nullable=false, length=64)
    private String inputHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="input_snapshot", nullable=false, columnDefinition="jsonb")
    private String inputSnapshot;
    @Column(name="status", nullable=false, length=32)
    private String status;
    @Column(name="summary", nullable=true, columnDefinition="text")
    private String summary;
    @Column(name="error_code", nullable=true, length=64)
    private String errorCode;
    @Column(name="attempt_count", nullable=false)
    private Integer attemptCount;
    @Column(name="retry_after", nullable=true)
    private OffsetDateTime retryAfter;
    @Column(name="started_at", nullable=false)
    private OffsetDateTime startedAt;
    @Column(name="completed_at", nullable=true)
    private OffsetDateTime completedAt;
    public PostingAnalysis() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String value) { this.sourceCode=value; }
    public String getSourcePostingId() { return sourcePostingId; }
    public void setSourcePostingId(String value) { this.sourcePostingId=value; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String value) { this.inputHash=value; }
    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String value) { this.inputSnapshot=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { this.summary=value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { this.errorCode=value; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { this.attemptCount=value; }
    public OffsetDateTime getRetryAfter() { return retryAfter; }
    public void setRetryAfter(OffsetDateTime value) { this.retryAfter=value; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime value) { this.startedAt=value; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime value) { this.completedAt=value; }
}
