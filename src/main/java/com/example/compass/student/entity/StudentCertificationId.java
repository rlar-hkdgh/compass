package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentCertificationId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="student_id")
    private Long studentId;
    @Column(name="certification_code", length=32)
    private String certificationCode;
    public StudentCertificationId() {}
    public StudentCertificationId(Long studentId, String certificationCode) {
        this.studentId=studentId;
        this.certificationCode=certificationCode;
    }
    public Long getStudentId() { return studentId; }
    public String getCertificationCode() { return certificationCode; }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof StudentCertificationId that)) return false;
        return Objects.equals(studentId,that.studentId) && Objects.equals(certificationCode,that.certificationCode);
    }
    @Override public int hashCode() { return Objects.hash(studentId,certificationCode); }
}
