package com.example.compass.student.entity;

import com.example.compass.student.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class StudentV5PersistenceTests {
    @Autowired StudentRepository students;
    @Autowired StudentInternshipRepository internships;
    @Autowired StudentAwardRepository awards;
    @Autowired StudentCertificationRepository certifications;
    @Autowired EntityManager em;
    private Student student() {
        var s=new Student();s.setLoginId("v5_student");s.setPasswordHash("hash");s.setSchoolEmail("v5@sju.ac.kr");s.setName("student");s.setIsTransfer(false);return students.saveAndFlush(s);
    }
    private StudentInternship internship(Long studentId) {
        var i=new StudentInternship();i.setStudentId(studentId);i.setCompanyName("company");i.setJobCategoryCode("84");i.setStartedOn(LocalDate.of(2025,1,1));i.setEndedOn(LocalDate.of(2025,2,1));i.setDescription("work + outcome");return i;
    }
    @Test void roundTripsInternJobCodeDatesAwardAndCertificateOwnership() {
        var s=student();var i=internships.saveAndFlush(internship(s.getId()));
        var a=new StudentAward();a.setStudentId(s.getId());a.setName("award");a.setOrganizer("organizer");a.setDescription("level + role + details");awards.saveAndFlush(a);
        var key=new StudentCertificationId(s.getId(),"QNET_1320");var cert=new StudentCertification();cert.setId(key);certifications.saveAndFlush(cert);em.clear();
        var restored=internships.findById(i.getId()).orElseThrow();assertThat(restored.getJobCategory().getCode()).isEqualTo("84");assertThat(restored.getStartedOn()).isEqualTo(LocalDate.of(2025,1,1));assertThat(restored.getEndedOn()).isEqualTo(LocalDate.of(2025,2,1));assertThat(restored.getDescription()).isEqualTo("work + outcome");
        assertThat(awards.findById(a.getId()).orElseThrow().getDescription()).isEqualTo("level + role + details");assertThat(certifications.findById(key).orElseThrow().getCertification().getName()).isEqualTo("정보처리기사");
    }
    @Test void rejectsFutureInternshipEndDateDuringPersistence() {
        var i=internship(student().getId());i.setEndedOn(LocalDate.now().plusDays(2));
        assertThatThrownBy(()->internships.saveAndFlush(i)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }
}
