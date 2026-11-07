package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="gap_analysis_category")
public class GapAnalysisCategory {
    @EmbeddedId
    private GapAnalysisCategoryId id;
    public GapAnalysisCategoryId getId() { return id; }
    public void setId(GapAnalysisCategoryId value) { id=value; }
    @Column(name="current_score", nullable=true, columnDefinition="numeric")
    private BigDecimal currentScore;
    @Column(name="required_score", nullable=true, columnDefinition="numeric")
    private BigDecimal requiredScore;
    @Column(name="assessment_status", nullable=false, length=32)
    private String assessmentStatus;
    @Column(name="explanation", nullable=false, columnDefinition="text")
    private String explanation;
    @Column(name="missing_information", nullable=true, columnDefinition="text")
    private String missingInformation;
    public GapAnalysisCategory() {}
    public BigDecimal getCurrentScore() { return currentScore; }
    public void setCurrentScore(BigDecimal value) { this.currentScore=value; }
    public BigDecimal getRequiredScore() { return requiredScore; }
    public void setRequiredScore(BigDecimal value) { this.requiredScore=value; }
    public String getAssessmentStatus() { return assessmentStatus; }
    public void setAssessmentStatus(String value) { this.assessmentStatus=value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { this.explanation=value; }
    public String getMissingInformation() { return missingInformation; }
    public void setMissingInformation(String value) { this.missingInformation=value; }
}
