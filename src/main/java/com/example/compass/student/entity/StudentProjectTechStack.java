package com.example.compass.student.entity;
import jakarta.persistence.*;
import com.example.compass.recruit.entity.TechStack;
@Entity
@Table(name="student_project_tech_stack")
public class StudentProjectTechStack {
    @EmbeddedId
    private StudentProjectTechStackId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="project_id",insertable=false,updatable=false)
    private StudentProject project;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="tech_stack_code",insertable=false,updatable=false)
    private TechStack techStack;
    public StudentProjectTechStack() {}
    public StudentProjectTechStackId getId() { return id; }
    public void setId(StudentProjectTechStackId value) { id=value; }
    public StudentProject getProject() { return project; }
    public TechStack getTechStack() { return techStack; }
}
