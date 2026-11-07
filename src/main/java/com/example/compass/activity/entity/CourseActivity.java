package com.example.compass.activity.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
@Entity
@Table(name="course_activity")
public class CourseActivity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;
    @Column(name="course_number",nullable=false,length=32)
    private String courseNumber;
    @Column(name="academic_year",nullable=false)
    private Integer academicYear;
    @Column(name="semester",nullable=false,length=32)
    private String semester;
    @Column(name="section_number",nullable=true,length=32)
    private String sectionNumber;
    @Column(name="name",nullable=false,length=500)
    private String name;
    @Column(name="department_code",nullable=true,length=32)
    private String departmentCode;
    @Column(name="department_name",nullable=true,length=255)
    private String departmentName;
    @Column(name="credits",nullable=true)
    private BigDecimal credits;
    @Column(name="description",nullable=false,columnDefinition="text")
    private String description;
    @Column(name="source_url",nullable=true,length=2000)
    private String sourceUrl;
    public Long getId(){return id;}
    public void setId(Long value){this.id=value;}
    public String getCourseNumber(){return courseNumber;}
    public void setCourseNumber(String value){this.courseNumber=value;}
    public Integer getAcademicYear(){return academicYear;}
    public void setAcademicYear(Integer value){this.academicYear=value;}
    public String getSemester(){return semester;}
    public void setSemester(String value){this.semester=value;}
    public String getSectionNumber(){return sectionNumber;}
    public void setSectionNumber(String value){this.sectionNumber=value;}
    public String getName(){return name;}
    public void setName(String value){this.name=value;}
    public String getDepartmentCode(){return departmentCode;}
    public void setDepartmentCode(String value){this.departmentCode=value;}
    public String getDepartmentName(){return departmentName;}
    public void setDepartmentName(String value){this.departmentName=value;}
    public BigDecimal getCredits(){return credits;}
    public void setCredits(BigDecimal value){this.credits=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){this.description=value;}
    public String getSourceUrl(){return sourceUrl;}
    public void setSourceUrl(String value){this.sourceUrl=value;}
}
