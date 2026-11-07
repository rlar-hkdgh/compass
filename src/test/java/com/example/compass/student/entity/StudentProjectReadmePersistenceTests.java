package com.example.compass.student.entity;

import com.example.compass.student.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class StudentProjectReadmePersistenceTests {
    @Autowired StudentRepository students;
    @Autowired StudentProjectRepository projects;
    @Autowired StudentProjectReadmeRepository readmes;
    @Autowired EntityManager em;

    @Test void roundTripsLargeMarkdownAndRetainsSuccessAfterFailedAttempt() {
        var student=new Student();student.setLoginId("readme_user");student.setPasswordHash("hash");student.setSchoolEmail("readme@sju.ac.kr");student.setName("student");student.setIsTransfer(false);students.saveAndFlush(student);
        var project=new StudentProject();project.setStudentId(student.getId());project.setOverview("프로젝트 목적과 핵심 기능");project.setName("project");project.setDescription("my contribution");project.setGithubUrl("https://github.com/example/repo");projects.saveAndFlush(project);
        assertThat(readmes.findById(project.getId())).isEmpty();
        var readme=new StudentProjectReadme();readme.setProjectId(project.getId());readme.setRepositoryUrl(project.getGithubUrl());
        var success=Instant.parse("2026-11-01T00:00:00Z");
        String body="# 프로젝트\n```java\nclass Example {}\n```\n".repeat(500);
        readme.setContent(body);readme.setReadmePath("README.md");readme.setReadmeSha("file-sha");readme.setLastSuccessAt(success);readme.setLastAttemptAt(success);readme.setLastAttemptStatus(ReadmeAttemptStatus.SUCCESS);
        readmes.saveAndFlush(readme);em.clear();
        var restored=readmes.findById(project.getId()).orElseThrow();
        assertThat(restored.getContent()).isEqualTo(body);assertThat(restored.getStudentProject().getDescription()).isEqualTo("my contribution");assertThat(restored.getLastSuccessAt()).isEqualTo(success);
        restored.setLastAttemptAt(success.plusSeconds(60));restored.setLastAttemptStatus(ReadmeAttemptStatus.FAILED);restored.setLastErrorCode("TIMEOUT");readmes.saveAndFlush(restored);em.clear();
        restored=readmes.findById(project.getId()).orElseThrow();
        assertThat(restored.getContent()).isEqualTo(body);assertThat(restored.getReadmeSha()).isEqualTo("file-sha");assertThat(restored.getLastSuccessAt()).isEqualTo(success);assertThat(restored.getLastAttemptStatus()).isEqualTo(ReadmeAttemptStatus.FAILED);assertThat(restored.getLastErrorCode()).isEqualTo("TIMEOUT");assertThat(restored.getLastAttemptAt()).isEqualTo(success.plusSeconds(60));
    }
}
