package com.example.compass.activity;

import com.example.compass.activity.entity.*;
import com.example.compass.roadmap.entity.*;
import com.example.compass.roadmap.service.RecommendedTaskService;
import com.example.compass.roadmap.repository.RecommendedTaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class RecommendedTaskTests {
    @Autowired EntityManager em;
    @Autowired RecommendedTaskService service;
    @Autowired RecommendedTaskRepository tasks;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private void seed(){
        em.createNativeQuery("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(701,'task_user','hash','task@sju.ac.kr','학생',false)").executeUpdate();
        em.createNativeQuery("INSERT INTO company(id,name) VALUES(701,'company')").executeUpdate();
        em.createNativeQuery("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','task',701,'posting',true)").executeUpdate();
        em.createNativeQuery("INSERT INTO posting_analysis(id,source_code,source_posting_id,input_hash,input_snapshot,status,started_at) VALUES(701,'SARAMIN','task','hash','{}','SUCCESS',CURRENT_TIMESTAMP)").executeUpdate();
        em.createNativeQuery("INSERT INTO gap_analysis(id,student_id,posting_analysis_id,evaluation_policy_id,student_input_hash,student_input_snapshot,status,started_at) VALUES(701,701,701,1,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)").executeUpdate();
    }
    private RecommendedTaskService.Draft draft(String title,int order){return new RecommendedTaskService.Draft(null,null,null,null,title,"자료구조 프로젝트 수행",2027,"FIRST_SEMESTER",order);}
    @Test void keepsOnlyCurrentTasksAndCompletesOnlyOnExplicitAction(){
        seed();service.replace(701L,701L,List.of(draft("이전 추천",1)));Long old=tasks.findCurrentByStudentId(701L).get(0).getId();
        service.updateStatus(701L,old,"COMPLETED");assertThat(tasks.findById(old).orElseThrow().getCompletedAt()).isNotNull();
        assertThat(em.createNativeQuery("SELECT count(*) FROM student_project WHERE student_id=701").getSingleResult().toString()).isEqualTo("0");
        service.replace(701L,701L,List.of(draft("현재 추천",1),draft("새 과제",2)));em.clear();
        assertThat(tasks.findById(old)).isEmpty();assertThat(tasks.findCurrentByStudentId(701L)).hasSize(2).allSatisfy(t->{assertThat(t.getStatus()).isEqualTo("TODO");assertThat(t.getCompletedAt()).isNull();});
    }
    @Test void rejectsBadReplacementWithoutRemovingCurrentTasks(){
        seed();service.replace(701L,701L,List.of(draft("유지",1)));
        assertThatThrownBy(()->service.replace(701L,701L,List.of(draft("중복",1),draft("중복2",1)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(tasks.findCurrentByStudentId(701L)).hasSize(1).first().extracting(RecommendedTask::getTitle).isEqualTo("유지");
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void storageFailureRollsBackTheDeletionOfExistingTasks(){
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status->{seed();service.replace(701L,701L,List.of(draft("keep current",1)));});
        try {
            tx.executeWithoutResult(status->em.createNativeQuery("ALTER TABLE recommended_task ADD CONSTRAINT test_reject_task CHECK(CASE WHEN title='database_reject' THEN 0 ELSE 1 END=1)").executeUpdate());
            assertThatThrownBy(()->service.replace(701L,701L,List.of(draft("database_reject",1)))).isInstanceOf(RuntimeException.class);
            String title=tx.execute(status->tasks.findCurrentByStudentId(701L).get(0).getTitle());assertThat(title).isEqualTo("keep current");
        } finally {
            tx.executeWithoutResult(status->{
                em.createNativeQuery("ALTER TABLE recommended_task DROP CONSTRAINT IF EXISTS test_reject_task").executeUpdate();
                for(String sql:List.of("DELETE FROM recommended_task WHERE student_id=701","DELETE FROM gap_analysis WHERE id=701","DELETE FROM posting_analysis WHERE id=701","DELETE FROM job_posting WHERE source_code='SARAMIN' AND source_posting_id='task'","DELETE FROM company WHERE id=701","DELETE FROM student WHERE id=701"))em.createNativeQuery(sql).executeUpdate();
            });
        }
    }
    @Test void linksCertificateAndExamWithoutUrlsAndChecksOwnership(){
        seed();var exam=new CertificationExamSchedule();exam.setCertificationCode("KDATA_SQLD");exam.setExamYear(2026);exam.setRoundNumber(60);exam.setExamStage("SINGLE");exam.setStartsOn(LocalDate.of(2026,3,7));exam.setEndsOn(exam.getStartsOn());em.persist(exam);em.flush();
        var d=new RecommendedTaskService.Draft(null,null,"KDATA_SQLD",exam.getId(),"SQLD 응시 준비","SQL 시험 범위 학습",2026,"FIRST_SEMESTER",1);service.replace(701L,701L,List.of(d));
        assertThat(tasks.findCurrentByStudentId(701L)).hasSize(1).first().extracting(RecommendedTask::getCertificationCode).isEqualTo("KDATA_SQLD");
        var wrong=new RecommendedTaskService.Draft(null,null,"KDATA_ADP",exam.getId(),"잘못된 연결","내용",2026,"FIRST_SEMESTER",1);
        assertThatThrownBy(()->service.replace(701L,701L,List.of(wrong))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.updateStatus(999L,tasks.findAll().get(0).getId(),"COMPLETED")).isInstanceOf(IllegalArgumentException.class);
    }
}
