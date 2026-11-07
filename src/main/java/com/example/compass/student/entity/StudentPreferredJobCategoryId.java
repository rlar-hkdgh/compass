package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentPreferredJobCategoryId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="student_id")
    private Long studentId;
    @Column(name="job_category_code", length=32)
    private String jobCategoryCode;
    public StudentPreferredJobCategoryId() {}
    public StudentPreferredJobCategoryId(Long studentId, String jobCategoryCode) {
        this.studentId=studentId;
        this.jobCategoryCode=jobCategoryCode;
    }
    public Long getStudentId() { return studentId; }
    public String getJobCategoryCode() { return jobCategoryCode; }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof StudentPreferredJobCategoryId that)) return false;
        return Objects.equals(studentId,that.studentId) && Objects.equals(jobCategoryCode,that.jobCategoryCode);
    }
    @Override public int hashCode() { return Objects.hash(studentId,jobCategoryCode); }
}
