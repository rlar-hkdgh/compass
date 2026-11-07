package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="gap_analysis")
public class GapAnalysis {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="student_id", nullable=false)
    private Long studentId;
    @Column(name="posting_analysis_id", nullable=false)
    private Long postingAnalysisId;
    @Column(name="evaluation_policy_id", nullable=false)
    private Long evaluationPolicyId;
    @Column(name="student_input_hash", nullable=false, length=64)
    private String studentInputHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="student_input_snapshot", nullable=false, columnDefinition="jsonb")
    private String studentInputSnapshot;
    @Column(name="status", nullable=false, length=32)
    private String status;
    @Column(name="overall_score", nullable=true, columnDefinition="numeric")
    private BigDecimal overallScore;
    @Column(name="summary", nullable=true, columnDefinition="text")
    private String summary;
    @Column(name="error_code", nullable=true, length=64)
    private String errorCode;
    @Column(name="started_at", nullable=false)
    private OffsetDateTime startedAt;
    @Column(name="completed_at", nullable=true)
    private OffsetDateTime completedAt;
    public GapAnalysis() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public Long getPostingAnalysisId() { return postingAnalysisId; }
    public void setPostingAnalysisId(Long value) { this.postingAnalysisId=value; }
    public Long getEvaluationPolicyId() { return evaluationPolicyId; }
    public void setEvaluationPolicyId(Long value) { this.evaluationPolicyId=value; }
    public String getStudentInputHash() { return studentInputHash; }
    public void setStudentInputHash(String value) { this.studentInputHash=value; }
    public String getStudentInputSnapshot() { return studentInputSnapshot; }
    public void setStudentInputSnapshot(String value) { this.studentInputSnapshot=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal value) { this.overallScore=value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { this.summary=value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { this.errorCode=value; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime value) { this.startedAt=value; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime value) { this.completedAt=value; }
}
