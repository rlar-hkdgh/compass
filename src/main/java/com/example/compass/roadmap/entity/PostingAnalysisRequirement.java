package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="posting_analysis_requirement")
public class PostingAnalysisRequirement {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="posting_analysis_id", nullable=false)
    private Long postingAnalysisId;
    @Column(name="category_code", nullable=false, length=32)
    private String categoryCode;
    @Column(name="name", nullable=false, length=255)
    private String name;
    @Column(name="requirement_type", nullable=false, length=32)
    private String requirementType;
    @Column(name="description", nullable=true, columnDefinition="text")
    private String description;
    @Column(name="evidence_text", nullable=false, columnDefinition="text")
    private String evidenceText;
    @Column(name="display_order", nullable=false)
    private Integer displayOrder;
    public PostingAnalysisRequirement() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getPostingAnalysisId() { return postingAnalysisId; }
    public void setPostingAnalysisId(Long value) { this.postingAnalysisId=value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { this.categoryCode=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getRequirementType() { return requirementType; }
    public void setRequirementType(String value) { this.requirementType=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String value) { this.evidenceText=value; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer value) { this.displayOrder=value; }
}
