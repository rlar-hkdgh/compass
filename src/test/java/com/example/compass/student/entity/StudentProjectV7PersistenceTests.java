package com.example.compass.student.entity;
import com.example.compass.student.repository.*;
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
class StudentProjectV7PersistenceTests {
 @Autowired StudentRepository students;
 @Autowired StudentProjectRepository projects;
 @Autowired StudentProjectTechStackRepository techLinks;
 @Autowired EntityManager em;
 private StudentProject project() {
  var s=new Student();s.setLoginId("project_v7");s.setPasswordHash("hash");s.setSchoolEmail("project_v7@sju.ac.kr");s.setName("student");s.setIsTransfer(false);students.saveAndFlush(s);
  var p=new StudentProject();p.setStudentId(s.getId());p.setName("project");p.setOverview("purpose and core functions");p.setDescription("my role and implementation");return p;
 }
 @Test void roundTripsOverviewContributionOptionalOutcomeAndMultipleTechReferences() {
  var p=projects.saveAndFlush(project());
  var codes=em.createQuery("select t.code from TechStack t order by t.code",String.class).setMaxResults(2).getResultList();
  for(String code:codes){var link=new StudentProjectTechStack();link.setId(new StudentProjectTechStackId(p.getId(),code));techLinks.saveAndFlush(link);}
  em.clear();var restored=projects.findById(p.getId()).orElseThrow();
  assertThat(restored.getOverview()).isEqualTo("purpose and core functions");assertThat(restored.getDescription()).isEqualTo("my role and implementation");assertThat(restored.getOutcome()).isNull();
  var links=techLinks.findAllByIdProjectId(p.getId());assertThat(links).hasSize(2);
  for(var link:links){assertThat(link.getProject().getId()).isEqualTo(p.getId());assertThat(link.getTechStack().getCode()).isIn(codes);}
  restored.setOutcome("reduced response time");projects.saveAndFlush(restored);em.clear();assertThat(projects.findById(p.getId()).orElseThrow().getOutcome()).isEqualTo("reduced response time");
 }
 @Test void rejectsMissingOverview() {var p=project();p.setOverview(null);assertThatThrownBy(()->projects.saveAndFlush(p)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);}
 @Test void rejectsBlankOverview() {var p=project();p.setOverview("  ");assertThatThrownBy(()->projects.saveAndFlush(p)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);}
 @Test void rejectsBlankContribution() {var p=project();p.setDescription("  ");assertThatThrownBy(()->projects.saveAndFlush(p)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);}
}
