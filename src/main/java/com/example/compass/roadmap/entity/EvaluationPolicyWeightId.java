package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Embeddable
public class EvaluationPolicyWeightId implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="policy_id", nullable=false)
    private Long policyId;
    @Column(name="category_code", nullable=false, length=32)
    private String categoryCode;
    public EvaluationPolicyWeightId() {}
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { this.policyId=value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { this.categoryCode=value; }
    @Override public boolean equals(Object o) { if(this==o) return true; if(!(o instanceof EvaluationPolicyWeightId that)) return false; return java.util.Objects.equals(policyId,that.policyId) && java.util.Objects.equals(categoryCode,that.categoryCode); }
    @Override public int hashCode() { return java.util.Objects.hash(policyId,categoryCode); }
}
