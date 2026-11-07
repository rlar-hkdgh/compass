package com.example.compass.roadmap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class RoadmapMigrationTests {
 @Test void upgradesV5PreservesDataAndEnforcesOwnershipCacheAndTaskConstraints() throws Exception {
  try(var pg=io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().setPort(0).start()) { String url=pg.getJdbcUrl("postgres","postgres");
  Flyway.configure().dataSource(url,"postgres","").target("5").load().migrate();
  try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
   s.execute("INSERT INTO company(id,name) VALUES(1,'keep')");
   s.execute("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','keep',1,'keep',true)");
   s.execute("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'user1','hash','one@sju.ac.kr','one',false),(2,'user2','hash','two@sju.ac.kr','two',false)");
  }
  var f=Flyway.configure().dataSource(url,"postgres","").target("6").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);
  try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
   try(var rs=s.executeQuery("SELECT title FROM job_posting WHERE source_posting_id='keep'")){rs.next();assertThat(rs.getString(1)).isEqualTo("keep");}
   String posting="INSERT INTO posting_analysis(id,source_code,source_posting_id,input_hash,input_snapshot,status,started_at) VALUES(1,'SARAMIN','keep','hash','{}','SUCCESS',CURRENT_TIMESTAMP)";
   s.execute(posting);assertThatThrownBy(()->s.execute(posting.replace("VALUES(1,","VALUES(2,"))).isInstanceOf(SQLException.class);
   s.execute("INSERT INTO gap_analysis(id,student_id,posting_analysis_id,evaluation_policy_id,student_input_hash,student_input_snapshot,status,started_at) VALUES(1,1,1,1,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO gap_analysis_category VALUES(1,'JOB_EXPERIENCE',NULL,80,'UNASSESSABLE','인턴과 프로젝트 수행 내용이 없어 판단할 수 없습니다.','수행 역할을 입력해 주세요.')");
   assertThatThrownBy(()->s.execute("UPDATE gap_analysis_category SET explanation=' '")).isInstanceOf(SQLException.class);
   s.execute("INSERT INTO career_roadmap(id,student_id,source_code,source_posting_id,gap_analysis_id,name,generation_status,created_at,updated_at) VALUES(1,1,'SARAMIN','keep',1,'plan','SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,1,'SARAMIN','keep',1,'new plan','SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO student_active_roadmap VALUES(1,'SARAMIN','keep',1,CURRENT_TIMESTAMP)");
   assertThatThrownBy(()->s.execute("INSERT INTO student_active_roadmap VALUES(1,'SARAMIN','keep',2,CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("INSERT INTO student_active_roadmap VALUES(2,'SARAMIN','keep',1,CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
   s.execute("UPDATE student_active_roadmap SET career_roadmap_id=2");
   s.execute("INSERT INTO roadmap_period(id,career_roadmap_id,academic_year,period_type,display_order) VALUES(1,2,2026,'SECOND_SEMESTER',1),(2,2,2026,'WINTER_BREAK',2),(3,2,2027,'FIRST_SEMESTER',3)");
   assertThatThrownBy(()->s.execute("INSERT INTO roadmap_period(career_roadmap_id,academic_year,period_type,display_order) VALUES(2,2026,'WINTER_BREAK',4)")).isInstanceOf(SQLException.class);
   s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,recommendation_reason,activity_type,display_order,status,created_at,updated_at) VALUES(1,1,'project','build','practice','PROJECT',1,'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO roadmap_task_category VALUES(1,'TECHNICAL_COMPETENCY',5,'practice'),(1,'JOB_EXPERIENCE',3,'experience')");
   assertThatThrownBy(()->s.execute("UPDATE roadmap_task_category SET expected_score_gain=-1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE roadmap_task SET status='COMPLETED'")).isInstanceOf(SQLException.class);
   s.execute("UPDATE roadmap_task SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP");
   assertThatThrownBy(()->s.execute("DELETE FROM career_roadmap WHERE id=2")).isInstanceOf(SQLException.class);
  }
 }
}


}
