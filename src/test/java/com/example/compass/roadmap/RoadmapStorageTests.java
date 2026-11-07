package com.example.compass.roadmap;
import com.example.compass.roadmap.entity.*;
import com.example.compass.roadmap.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.context.ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@SpringBootTest
@Transactional
class RoadmapStorageTests {
 @Autowired EvaluationCategoryRepository categories;
 @Autowired EvaluationPolicyWeightRepository weights;
 @Autowired PostingAnalysisRepository analyses;
 @Autowired EntityManager em;
 @Test void seedsSixCategoriesAndCommonWeights() {
  assertThat(categories.count()).isEqualTo(6);
  assertThat(weights.findAll()).hasSize(6);
  assertThat(weights.findAll().stream().mapToInt(EvaluationPolicyWeight::getWeight).sum()).isEqualTo(100);
 }
 @Test void roundTripsJsonAndSharedPostingAnalysis() {
  em.createNativeQuery("INSERT INTO company(id,name) VALUES(90001,'roadmap')").executeUpdate();
  em.createNativeQuery("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','roadmap-test',90001,'title',true)").executeUpdate();
  var a=new PostingAnalysis();a.setSourceCode("SARAMIN");a.setSourcePostingId("roadmap-test");a.setInputHash("a".repeat(64));a.setInputSnapshot("{\"title\":\"test\"}");a.setStatus("SUCCESS");a.setAttemptCount(1);a.setStartedAt(OffsetDateTime.now());a.setCompletedAt(OffsetDateTime.now());
  analyses.saveAndFlush(a);em.clear();
  var restored=analyses.findById(a.getId()).orElseThrow();
  assertThat(restored.getInputSnapshot()).contains("title");assertThat(restored.getStatus()).isEqualTo("SUCCESS");
 }
}
