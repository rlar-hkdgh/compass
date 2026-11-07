package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import jakarta.validation.constraints.PastOrPresent;
import com.example.compass.recruit.entity.JobCategory;

@Entity
@Table(name="student_internship")
public class StudentInternship {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="student_id", nullable=false)
    private Long studentId;
    @Column(name="company_name", nullable=false, length=255)
    private String companyName;
    @Column(name="job_category_code", nullable=false, length=32)
    private String jobCategoryCode;
    @Column(name="started_on", nullable=false)
    private LocalDate startedOn;
    @PastOrPresent
    @Column(name="ended_on", nullable=false)
    private LocalDate endedOn;
    @Column(name="description", nullable=false, columnDefinition="text")
    private String description;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="job_category_code", insertable=false, updatable=false)
    private JobCategory jobCategory;

    public StudentInternship() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String value) { this.companyName=value; }
    public String getJobCategoryCode() { return jobCategoryCode; }
    public void setJobCategoryCode(String value) { this.jobCategoryCode=value; }
    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate value) { this.startedOn=value; }
    public LocalDate getEndedOn() { return endedOn; }
    public void setEndedOn(LocalDate value) { this.endedOn=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public Student getStudent() { return student; }
    public JobCategory getJobCategory() { return jobCategory; }
}
