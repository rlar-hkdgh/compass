package com.example.compass.student.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name="student_project")
public class StudentProject {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;
    @Column(name="student_id", nullable=false)
    private Long studentId;
    @Column(name="name", nullable=false, length=255)
    private String name;
    @NotBlank
    @Column(name="overview", nullable=false, columnDefinition="text")
    private String overview;
    @Column(name="outcome", columnDefinition="text")
    private String outcome;
    @NotBlank
    @Column(name="description", nullable=false, columnDefinition="text")
    private String description;
    @Column(name="github_url", nullable=true, length=2000)
    private String githubUrl;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", insertable=false, updatable=false)
    private Student student;

    public StudentProject() {}
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { this.studentId=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getOverview() { return overview; }
    public void setOverview(String value) { this.overview=value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { this.outcome=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String value) { this.githubUrl=value; }
    public Student getStudent() { return student; }
}
