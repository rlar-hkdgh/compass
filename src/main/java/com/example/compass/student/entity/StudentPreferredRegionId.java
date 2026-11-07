package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentPreferredRegionId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="student_id")
    private Long studentId;
    @Column(name="region_code", length=32)
    private String regionCode;
    public StudentPreferredRegionId() {}
    public StudentPreferredRegionId(Long studentId, String regionCode) {
        this.studentId=studentId;
        this.regionCode=regionCode;
    }
    public Long getStudentId() { return studentId; }
    public String getRegionCode() { return regionCode; }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof StudentPreferredRegionId that)) return false;
        return Objects.equals(studentId,that.studentId) && Objects.equals(regionCode,that.regionCode);
    }
    @Override public int hashCode() { return Objects.hash(studentId,regionCode); }
}
