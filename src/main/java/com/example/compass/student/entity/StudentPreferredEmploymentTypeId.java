package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentPreferredEmploymentTypeId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="student_id")
    private Long studentId;
    @Column(name="employment_type_code", length=32)
    private String employmentTypeCode;
    public StudentPreferredEmploymentTypeId() {}
    public StudentPreferredEmploymentTypeId(Long studentId, String employmentTypeCode) {
        this.studentId=studentId;
        this.employmentTypeCode=employmentTypeCode;
    }
    public Long getStudentId() { return studentId; }
    public String getEmploymentTypeCode() { return employmentTypeCode; }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof StudentPreferredEmploymentTypeId that)) return false;
        return Objects.equals(studentId,that.studentId) && Objects.equals(employmentTypeCode,that.employmentTypeCode);
    }
    @Override public int hashCode() { return Objects.hash(studentId,employmentTypeCode); }
}
