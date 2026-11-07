package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Embeddable
public class GapAnalysisCategoryId implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="gap_analysis_id", nullable=false)
    private Long gapAnalysisId;
    @Column(name="category_code", nullable=false, length=32)
    private String categoryCode;
    public GapAnalysisCategoryId() {}
    public Long getGapAnalysisId() { return gapAnalysisId; }
    public void setGapAnalysisId(Long value) { this.gapAnalysisId=value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { this.categoryCode=value; }
    @Override public boolean equals(Object o) { if(this==o) return true; if(!(o instanceof GapAnalysisCategoryId that)) return false; return java.util.Objects.equals(gapAnalysisId,that.gapAnalysisId) && java.util.Objects.equals(categoryCode,that.categoryCode); }
    @Override public int hashCode() { return java.util.Objects.hash(gapAnalysisId,categoryCode); }
}
