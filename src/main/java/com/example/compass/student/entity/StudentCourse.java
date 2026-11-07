package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 학생 수강 기록 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_course")
public class StudentCourse {
    /** 수강 기록 식별자 */
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    /** 학생 */
    @Column(name="student_id", nullable=false)
    private Long studentId;
    /** 학수번호; 앞자리 0 보존 */
    @Column(name="course_number", nullable=false, length=32)
    private String courseNumber;
    /** 교과목명 */
    @Column(name="course_name", nullable=false, length=255)
    private String courseName;
    /** 수강 연도 */
    @Column(name="academic_year", nullable=false)
    private Integer academicYear;
    /** 학기; 계절학기 포함 원문 보존 */
    @Column(name="semester", nullable=false, length=32)
    private String semester;
    /** 평가방식; GRADE 또는 P/NP 등 */
    @Column(name="evaluation_method", nullable=false, length=32)
    private String evaluationMethod;
    /** 등급; A+·B0·P·NP 등 */
    @Column(name="grade", nullable=false, length=16)
    private String grade;
    /** 평점; 미제공 시 NULL */
    @Column(name="grade_point", nullable=true, precision=4, scale=2)
    private BigDecimal gradePoint;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;

    public StudentCourse() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public String getCourseNumber() { return courseNumber; }
    public void setCourseNumber(String value) { this.courseNumber=value; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String value) { this.courseName=value; }
    public Integer getAcademicYear() { return academicYear; }
    public void setAcademicYear(Integer value) { this.academicYear=value; }
    public String getSemester() { return semester; }
    public void setSemester(String value) { this.semester=value; }
    public String getEvaluationMethod() { return evaluationMethod; }
    public void setEvaluationMethod(String value) { this.evaluationMethod=value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { this.grade=value; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal value) { this.gradePoint=value; }
    public Student getStudent() { return student; }
}
