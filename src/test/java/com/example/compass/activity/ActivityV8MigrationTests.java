package com.example.compass.activity;
import org.flywaydb.core.Flyway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class ActivityV8MigrationTests {
 static EmbeddedPostgres pg;
 @BeforeAll static void start() throws Exception {pg=EmbeddedPostgres.builder().setPort(0).start();}
 @AfterAll static void stop() throws Exception {if(pg!=null)pg.close();}
 private String v7() throws Exception {
  String db="activity_"+UUID.randomUUID().toString().replace("-","");
  try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
  String url=pg.getJdbcUrl("postgres",db);Flyway.configure().dataSource(url,"postgres","").target("7").load().migrate();return url;
 }
 private void base(Statement s) throws SQLException {
  s.execute("INSERT INTO company(id,name) VALUES(1,'keep company')");
  s.execute("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'keep_user','hash','keep@sju.ac.kr','keep student',false)");
  s.execute("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','keep',1,'keep posting',true)");
  s.execute("INSERT INTO posting_analysis(id,source_code,source_posting_id,input_hash,input_snapshot,status,started_at) VALUES(1,'SARAMIN','keep','hash','{}','SUCCESS',CURRENT_TIMESTAMP)");
  s.execute("INSERT INTO gap_analysis(id,student_id,posting_analysis_id,evaluation_policy_id,student_input_hash,student_input_snapshot,status,started_at) VALUES(1,1,1,1,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)");
  s.execute("INSERT INTO gap_analysis_category VALUES(1,'JOB_EXPERIENCE',NULL,80,'UNASSESSABLE','Missing internship details','Add personal contributions')");
  s.execute("INSERT INTO career_roadmap(id,student_id,source_code,source_posting_id,gap_analysis_id,name,generation_status,created_at,updated_at) VALUES(1,1,'SARAMIN','keep',1,'plan','SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  s.execute("INSERT INTO roadmap_period(id,career_roadmap_id,academic_year,period_type,display_order) VALUES(1,1,2026,'WINTER_BREAK',1)");
 }
 @Test void upgradesV7AndEnforcesActivitiesTaskTypesSnapshotsAndRestrict() throws Exception {
  String url=v7();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){base(s);}
  var f=Flyway.configure().dataSource(url,"postgres","").target("8").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
  try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
   for(String[] pair:new String[][]{{"activity_type","7"},{"activity_source","12"},{"evaluation_category","6"},{"evaluation_policy_weight","6"}})try(var rs=s.executeQuery("SELECT count(*) FROM "+pair[0])){rs.next();assertThat(rs.getInt(1)).isEqualTo(Integer.parseInt(pair[1]));}
   try(var rs=s.executeQuery("SELECT explanation,current_score,required_score FROM gap_analysis_category")){rs.next();assertThat(rs.getString(1)).isEqualTo("Missing internship details");assertThat(rs.getBigDecimal(2)).isNull();assertThat(rs.getInt(3)).isEqualTo(80);}
   String[] types={"COURSE","CERTIFICATION","EXTRACURRICULAR","INTERNSHIP","BOOTCAMP"};
   for(int i=0;i<types.length;i++)s.execute("INSERT INTO career_activity(id,activity_type_code,source_code,source_activity_id,name,provider_name,description,delivery_mode,availability_status,is_active,created_at,updated_at) VALUES("+(i+1)+",'"+types[i]+"','MANUAL','"+i+"','name','provider','description','UNKNOWN','UNKNOWN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO course_activity(activity_id,course_number,academic_year,semester) VALUES(1,'001234',2026,'2')");
   s.execute("INSERT INTO certification_activity(activity_id,certification_code) VALUES(2,'QNET_1320')");
   s.execute("INSERT INTO extracurricular_activity(activity_id,program_id) VALUES(3,'dodream-1')");
   s.execute("INSERT INTO internship_activity(activity_id,company_name,company_id) VALUES(4,'keep company',1)");
   s.execute("INSERT INTO bootcamp_activity(activity_id,cohort_name) VALUES(5,'cohort 1')");
   assertThatThrownBy(()->s.execute("UPDATE career_activity SET source_activity_id='duplicate'")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE career_activity SET activity_type_code='PROJECT' WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE career_activity SET source_code='WORK24',source_activity_id=NULL WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE career_activity SET starts_on=DATE '2026-12-01',ends_on=DATE '2026-11-01' WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE career_activity SET delivery_mode='INVALID' WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE certification_activity SET certification_code='UNKNOWN'")).isInstanceOf(SQLException.class);
   s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,activity_type,display_order,status,created_at,updated_at) VALUES(1,1,'own project','build','PROJECT',1,'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,activity_type,activity_id,activity_snapshot,display_order,status,created_at,updated_at) VALUES(2,1,'course','study','COURSE',1,'{\"name\":\"original\"}',2,'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   assertThatThrownBy(()->s.execute("UPDATE roadmap_task SET activity_type='BOOTCAMP' WHERE id=2")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE roadmap_task SET activity_snapshot=NULL WHERE id=2")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE roadmap_task SET activity_id=999 WHERE id=2")).isInstanceOf(SQLException.class);
   s.execute("UPDATE career_activity SET name='changed' WHERE id=1");
   try(var rs=s.executeQuery("SELECT activity_snapshot FROM roadmap_task WHERE id=2")){rs.next();assertThat(rs.getString(1)).contains("original");}
   assertThatThrownBy(()->s.execute("DELETE FROM career_activity WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeQuery("SELECT recommendation_reason FROM roadmap_task")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeQuery("SELECT * FROM roadmap_task_category")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeQuery("SELECT * FROM career_activity_category")).isInstanceOf(SQLException.class);
  }
 }
 @Test void refusesToDiscardExistingReasonsOrCategoryScoresBeforeChangingSchema() throws Exception {
  for(boolean category:new boolean[]{false,true}) {
   String url=v7();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
    base(s);s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,recommendation_reason,activity_type,display_order,status,created_at,updated_at) VALUES(1,1,'keep','work','original reason','PROJECT',1,'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    if(category)s.execute("INSERT INTO roadmap_task_category VALUES(1,'JOB_EXPERIENCE',3,'original explanation')");
   }
   assertThatThrownBy(()->Flyway.configure().dataSource(url,"postgres","").load().migrate()).isInstanceOf(Exception.class);
   try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
    try(var rs=s.executeQuery("SELECT recommendation_reason FROM roadmap_task")){rs.next();assertThat(rs.getString(1)).isEqualTo("original reason");}
    if(category)try(var rs=s.executeQuery("SELECT expected_score_gain,explanation FROM roadmap_task_category")){rs.next();assertThat(rs.getInt(1)).isEqualTo(3);assertThat(rs.getString(2)).isEqualTo("original explanation");}
    assertThatThrownBy(()->s.executeQuery("SELECT * FROM career_activity")).isInstanceOf(SQLException.class);
   }
  }
 }
}
