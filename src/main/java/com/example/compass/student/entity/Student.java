package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import jakarta.validation.constraints.PastOrPresent;
import com.example.compass.recruit.entity.JobCategory;

@Entity
@Table(name="student")
public class Student {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    // Legacy identifiers are retained; authentication uses schoolEmail.
    @Column(name="login_id", length=20)
    private String loginId;
    @Column(name="password_hash", nullable=false, length=255)
    private String passwordHash;
    @Column(name="school_email", nullable=false, length=254)
    private String schoolEmail;
    @Column(name="name", nullable=false, length=100)
    private String name;
    @Column(name="is_transfer", nullable=false)
    private Boolean isTransfer;

    @Column(name="student_number",length=8) private String studentNumber;
    @Column(name="semester") private Integer semester;
    @Column(name="gpa",precision=3,scale=2) private java.math.BigDecimal gpa;
    @Column(name="gpa_scale",precision=3,scale=2) private java.math.BigDecimal gpaScale;

    public String getStudentNumber(){return studentNumber;}
    public void setStudentNumber(String value){studentNumber=value;}
    public Integer getSemester(){return semester;}
    public void setSemester(Integer value){semester=value;}
    public java.math.BigDecimal getGpa(){return gpa;}
    public void setGpa(java.math.BigDecimal value){gpa=value;}
    public java.math.BigDecimal getGpaScale(){return gpaScale;}
    public void setGpaScale(java.math.BigDecimal value){gpaScale=value;}

    public Student() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String value) { this.loginId=value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash=value; }
    public String getSchoolEmail() { return schoolEmail; }
    public void setSchoolEmail(String value) { this.schoolEmail=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public Boolean getIsTransfer() { return isTransfer; }
    public void setIsTransfer(Boolean value) { this.isTransfer=value; }
}
