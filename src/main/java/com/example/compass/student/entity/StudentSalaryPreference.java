package com.example.compass.student.entity;
import com.example.compass.recruit.entity.SalaryUnit;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 희망 급여 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_salary_preference")
public class StudentSalaryPreference {
    /** 학생 */
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="student_id", nullable=false)
    private Long studentId;
    /** 지급 단위 */
    @Column(name="salary_unit_code", nullable=false, length=32)
    private String salaryUnitCode;
    /** 희망 최소 금액; 원 단위 */
    @Column(name="min_amount", nullable=false)
    private Long minAmount;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="salary_unit_code", referencedColumnName="code", insertable=false, updatable=false)
    private SalaryUnit salaryUnit;

    public StudentSalaryPreference() {}
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public String getSalaryUnitCode() { return salaryUnitCode; }
    public void setSalaryUnitCode(String value) { this.salaryUnitCode=value; }
    public Long getMinAmount() { return minAmount; }
    public void setMinAmount(Long value) { this.minAmount=value; }
    public Student getStudent() { return student; }
    public SalaryUnit getSalaryUnit() { return salaryUnit; }
}
