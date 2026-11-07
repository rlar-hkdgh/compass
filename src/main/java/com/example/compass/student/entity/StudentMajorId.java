package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentMajorId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="student_id")
    private Long studentId;
    @Column(name="major_code", length=32)
    private String majorCode;
    @Column(name="completion_type_code", length=32)
    private String completionTypeCode;
    public StudentMajorId() {}
    public StudentMajorId(Long studentId, String majorCode, String completionTypeCode) {
        this.studentId=studentId;
        this.majorCode=majorCode;
        this.completionTypeCode=completionTypeCode;
    }
    public Long getStudentId() { return studentId; }
    public String getMajorCode() { return majorCode; }
    public String getCompletionTypeCode() { return completionTypeCode; }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof StudentMajorId that)) return false;
        return Objects.equals(studentId,that.studentId) && Objects.equals(majorCode,that.majorCode) && Objects.equals(completionTypeCode,that.completionTypeCode);
    }
    @Override public int hashCode() { return Objects.hash(studentId,majorCode,completionTypeCode); }
}
