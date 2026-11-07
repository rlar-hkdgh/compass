package com.example.compass.student.entity;
import com.example.compass.recruit.entity.JobCategory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 희망 직무 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_preferred_job_category")
public class StudentPreferredJobCategory {
    @EmbeddedId
    private StudentPreferredJobCategoryId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="job_category_code", referencedColumnName="code", insertable=false, updatable=false)
    private JobCategory jobCategory;

    public StudentPreferredJobCategory() {}
    public StudentPreferredJobCategoryId getId() { return id; }
    public void setId(StudentPreferredJobCategoryId id) { this.id=id; }
    public Student getStudent() { return student; }
    public JobCategory getJobCategory() { return jobCategory; }
}
