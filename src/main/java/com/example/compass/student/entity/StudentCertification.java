package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import jakarta.validation.constraints.PastOrPresent;
import com.example.compass.recruit.entity.JobCategory;

@Entity
@Table(name="student_certification")
public class StudentCertification {
    @EmbeddedId
    private StudentCertificationId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="certification_code", insertable=false, updatable=false)
    private Certification certification;

    public StudentCertification() {}
    public StudentCertificationId getId() { return id; }
    public void setId(StudentCertificationId value) { this.id=value; }
    public Student getStudent() { return student; }
    public Certification getCertification() { return certification; }
}
