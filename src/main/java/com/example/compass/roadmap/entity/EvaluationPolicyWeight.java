package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="evaluation_policy_weight")
public class EvaluationPolicyWeight {
    @EmbeddedId
    private EvaluationPolicyWeightId id;
    public EvaluationPolicyWeightId getId() { return id; }
    public void setId(EvaluationPolicyWeightId value) { id=value; }
    @Column(name="weight", nullable=false)
    private Integer weight;
    public EvaluationPolicyWeight() {}
    public Integer getWeight() { return weight; }
    public void setWeight(Integer value) { this.weight=value; }
}
