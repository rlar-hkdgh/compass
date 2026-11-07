package com.example.compass.activity.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="activity_type")
public class ActivityType {
    @Id
    @Column(name="code", nullable=false, length=32)
    private String code;
    @Column(name="name", nullable=false, length=100)
    private String name;
    @Column(name="display_order", nullable=false)
    private Integer displayOrder;
    public ActivityType() {}
    public String getCode() { return code; }
    public void setCode(String value) { this.code=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer value) { this.displayOrder=value; }
}
