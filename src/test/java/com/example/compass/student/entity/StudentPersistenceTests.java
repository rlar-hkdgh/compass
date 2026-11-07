package com.example.compass.student.entity;

import com.example.compass.student.repository.MajorRepository;
import com.example.compass.student.repository.StudentMajorRepository;
import com.example.compass.student.repository.StudentProjectRepository;

import com.example.compass.student.repository.StudentRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class StudentPersistenceTests {
    @Autowired StudentRepository students;
    @Autowired StudentProjectRepository projects;
    @Autowired StudentMajorRepository studentMajors;
    @Autowired MajorRepository majors;
    @Autowired EntityManager em;

    private Student student() {
        var s=new Student();
        s.setLoginId("test_user");s.setPasswordHash("stored-password-hash");
        s.setSchoolEmail("test_user@sju.ac.kr");s.setName("학생");s.setIsTransfer(false);
        return students.saveAndFlush(s);
    }
    @Test void roundTripsCompletedProjectAndStudentReference() {
        var student=student();
        var project=new StudentProject();project.setStudentId(student.getId());project.setOverview("프로젝트 목적과 핵심 기능");project.setName("프로젝트");
        project.setDescription("역할·구현 내용·성과");project.setGithubUrl("https://github.com/example/repo");
        project=projects.saveAndFlush(project);em.clear();
        var restored=projects.findById(project.getId()).orElseThrow();
        assertThat(restored.getDescription()).isEqualTo("역할·구현 내용·성과");
        assertThat(restored.getStudent().getName()).isEqualTo("학생");
    }
    @Test void loginRenameDoesNotChangeStudentMajorReference() {
        var s=student();
        var major=new Major();major.setCode("TEST");major.setName("테스트전공");majors.saveAndFlush(major);
        var key=new StudentMajorId(s.getId(),"TEST","PRIMARY");
        var link=new StudentMajor();link.setId(key);studentMajors.saveAndFlush(link);
        s.setLoginId("renamed");students.saveAndFlush(s);em.clear();
        assertThat(studentMajors.findById(key).orElseThrow().getStudent().getLoginId()).isEqualTo("renamed");
    }
}
