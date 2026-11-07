package com.example.compass.roadmap.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
@Entity
@Table(name="recommended_task")
public class RecommendedTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;
    @Column(name="student_id",nullable=false)
    private Long studentId;
    @Column(name="gap_analysis_id",nullable=false)
    private Long gapAnalysisId;
    @Column(name="course_activity_id",nullable=true)
    private Long courseActivityId;
    @Column(name="career_activity_id",nullable=true)
    private Long careerActivityId;
    @Column(name="certification_code",nullable=true,length=32)
    private String certificationCode;
    @Column(name="certification_exam_schedule_id",nullable=true)
    private Long certificationExamScheduleId;
    @Column(name="title",nullable=false,length=255)
    private String title;
    @Column(name="description",nullable=false,columnDefinition="text")
    private String description;
    @Column(name="academic_year",nullable=false)
    private Integer academicYear;
    @Column(name="period_type",nullable=false,length=32)
    private String periodType;
    @Column(name="display_order",nullable=false)
    private Integer displayOrder;
    @Column(name="status",nullable=false,length=32)
    private String status;
    @Column(name="completed_at",nullable=true)
    private OffsetDateTime completedAt;
    public Long getId(){return id;}
    public void setId(Long value){this.id=value;}
    public Long getStudentId(){return studentId;}
    public void setStudentId(Long value){this.studentId=value;}
    public Long getGapAnalysisId(){return gapAnalysisId;}
    public void setGapAnalysisId(Long value){this.gapAnalysisId=value;}
    public Long getCourseActivityId(){return courseActivityId;}
    public void setCourseActivityId(Long value){this.courseActivityId=value;}
    public Long getCareerActivityId(){return careerActivityId;}
    public void setCareerActivityId(Long value){this.careerActivityId=value;}
    public String getCertificationCode(){return certificationCode;}
    public void setCertificationCode(String value){this.certificationCode=value;}
    public Long getCertificationExamScheduleId(){return certificationExamScheduleId;}
    public void setCertificationExamScheduleId(Long value){this.certificationExamScheduleId=value;}
    public String getTitle(){return title;}
    public void setTitle(String value){this.title=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){this.description=value;}
    public Integer getAcademicYear(){return academicYear;}
    public void setAcademicYear(Integer value){this.academicYear=value;}
    public String getPeriodType(){return periodType;}
    public void setPeriodType(String value){this.periodType=value;}
    public Integer getDisplayOrder(){return displayOrder;}
    public void setDisplayOrder(Integer value){this.displayOrder=value;}
    public String getStatus(){return status;}
    public void setStatus(String value){this.status=value;}
    public OffsetDateTime getCompletedAt(){return completedAt;}
    public void setCompletedAt(OffsetDateTime value){this.completedAt=value;}
}
