package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Latest README snapshot per project. No fetching or automatic lifecycle operations. */
@Entity
@Table(name="student_project_readme")
public class StudentProjectReadme {
    @Id
    @Column(name="project_id", nullable=false)
    private Long projectId;
    @Column(name="repository_url", nullable=false, length=2000)
    private String repositoryUrl;
    @Column(name="readme_path", columnDefinition="text")
    private String readmePath;
    @Column(name="readme_sha", length=128)
    private String readmeSha;
    @Column(name="content", columnDefinition="text")
    private String content;
    @Column(name="last_success_at")
    private Instant lastSuccessAt;
    @Column(name="last_attempt_at", nullable=false)
    private Instant lastAttemptAt;
    @Enumerated(EnumType.STRING)
    @Column(name="last_attempt_status", nullable=false, length=32)
    private ReadmeAttemptStatus lastAttemptStatus;
    @Column(name="last_error_code", length=64)
    private String lastErrorCode;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="project_id", insertable=false, updatable=false)
    private StudentProject studentProject;

    public StudentProjectReadme() {}
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { this.projectId=value; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String value) { this.repositoryUrl=value; }
    public String getReadmePath() { return readmePath; }
    public void setReadmePath(String value) { this.readmePath=value; }
    public String getReadmeSha() { return readmeSha; }
    public void setReadmeSha(String value) { this.readmeSha=value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content=value; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant value) { this.lastSuccessAt=value; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant value) { this.lastAttemptAt=value; }
    public ReadmeAttemptStatus getLastAttemptStatus() { return lastAttemptStatus; }
    public void setLastAttemptStatus(ReadmeAttemptStatus value) { this.lastAttemptStatus=value; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String value) { this.lastErrorCode=value; }
    public StudentProject getStudentProject() { return studentProject; }
}
