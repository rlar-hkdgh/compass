package com.example.compass.student.entity;
import com.example.compass.recruit.entity.EmploymentType;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 희망 근무형태 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_preferred_employment_type")
public class StudentPreferredEmploymentType {
    @EmbeddedId
    private StudentPreferredEmploymentTypeId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="employment_type_code", referencedColumnName="code", insertable=false, updatable=false)
    private EmploymentType employmentType;

    public StudentPreferredEmploymentType() {}
    public StudentPreferredEmploymentTypeId getId() { return id; }
    public void setId(StudentPreferredEmploymentTypeId id) { this.id=id; }
    public Student getStudent() { return student; }
    public EmploymentType getEmploymentType() { return employmentType; }
}
