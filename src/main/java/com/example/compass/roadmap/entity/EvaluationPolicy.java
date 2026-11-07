package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="evaluation_policy")
public class EvaluationPolicy {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="version", nullable=false)
    private Integer version;
    @Column(name="name", nullable=false, length=100)
    private String name;
    @Column(name="effective_from", nullable=false)
    private OffsetDateTime effectiveFrom;
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
    public EvaluationPolicy() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime value) { this.effectiveFrom=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
}
