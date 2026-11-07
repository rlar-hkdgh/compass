package com.example.compass.student.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class StudentProjectTechStackId implements Serializable {
    private static final long serialVersionUID=1L;
    @Column(name="project_id", nullable=false)
    private Long projectId;
    @Column(name="tech_stack_code", nullable=false, length=32)
    private String techStackCode;
    public StudentProjectTechStackId() {}
    public StudentProjectTechStackId(Long projectId,String techStackCode) { this.projectId=projectId;this.techStackCode=techStackCode; }
    public Long getProjectId() { return projectId; }
    public String getTechStackCode() { return techStackCode; }
    @Override public boolean equals(Object o) { if(this==o)return true;if(!(o instanceof StudentProjectTechStackId that))return false;return Objects.equals(projectId,that.projectId)&&Objects.equals(techStackCode,that.techStackCode); }
    @Override public int hashCode() { return Objects.hash(projectId,techStackCode); }
}
