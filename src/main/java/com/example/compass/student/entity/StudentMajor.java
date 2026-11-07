package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 학생 전공 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_major")
public class StudentMajor {
    @EmbeddedId
    private StudentMajorId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="major_code", referencedColumnName="code", insertable=false, updatable=false)
    private Major major;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="completion_type_code", referencedColumnName="code", insertable=false, updatable=false)
    private MajorCompletionType majorCompletionType;

    public StudentMajor() {}
    public StudentMajorId getId() { return id; }
    public void setId(StudentMajorId id) { this.id=id; }
    public Student getStudent() { return student; }
    public Major getMajor() { return major; }
    public MajorCompletionType getMajorCompletionType() { return majorCompletionType; }
}
