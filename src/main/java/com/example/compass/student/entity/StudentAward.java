package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import jakarta.validation.constraints.PastOrPresent;
import com.example.compass.recruit.entity.JobCategory;

@Entity
@Table(name="student_award")
public class StudentAward {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="student_id", nullable=false)
    private Long studentId;
    @Column(name="name", nullable=false, length=255)
    private String name;
    @Column(name="organizer", nullable=false, length=255)
    private String organizer;
    @Column(name="description", nullable=true, columnDefinition="text")
    private String description;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", insertable=false, updatable=false)
    private Student student;

    public StudentAward() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String value) { this.organizer=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public Student getStudent() { return student; }
}
