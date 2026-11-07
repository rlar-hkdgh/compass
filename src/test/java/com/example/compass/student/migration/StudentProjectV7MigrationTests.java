package com.example.compass.student.migration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class StudentProjectV7MigrationTests {
 private String v6() {
  String url="jdbc:h2:mem:project-v7-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("6").load().migrate();return url;
 }
 private void student(Statement s) throws SQLException {
  s.execute("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'v7_user','hash','v7@sju.ac.kr','student',false)");
 }
 @Test void upgradesEmptyProjectsAndEnforcesRequiredTextAndMultipleExistingTechCodes() throws Exception {
  String url=v6();var f=Flyway.configure().dataSource(url,"sa","").target("7").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
   student(s);
   s.execute("INSERT INTO student_project(id,student_id,name,overview,description) VALUES(1,1,'project','purpose','personal work')");
   s.execute("INSERT INTO student_project_tech_stack SELECT 1,code FROM tech_stack ORDER BY code LIMIT 2");
   try(var rs=s.executeQuery("SELECT count(*) FROM student_project_tech_stack")){rs.next();assertThat(rs.getInt(1)).isEqualTo(2);}
   assertThatThrownBy(()->s.execute("INSERT INTO student_project_tech_stack SELECT * FROM student_project_tech_stack")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("INSERT INTO student_project_tech_stack VALUES(1,'UNKNOWN')")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("INSERT INTO student_project_tech_stack SELECT 999,code FROM tech_stack LIMIT 1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE student_project SET overview=NULL")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE student_project SET overview=' '")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("UPDATE student_project SET description=' '")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("DELETE FROM student_project WHERE id=1")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.execute("DELETE FROM tech_stack WHERE code IN (SELECT tech_stack_code FROM student_project_tech_stack)")).isInstanceOf(SQLException.class);
   try(var rs=s.executeQuery("SELECT outcome FROM student_project WHERE id=1")){rs.next();assertThat(rs.getString(1)).isNull();}
   s.execute("UPDATE student_project SET outcome='measured result'");
   try(var rs=s.executeQuery("SELECT count(*) FROM tech_stack")){rs.next();assertThat(rs.getInt(1)).isEqualTo(140);}
  }
 }
 @Test void existingProjectsStopBeforeAnySchemaChangeAndKeepOriginalDescription() throws Exception {
  String url=v6();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
   student(s);s.execute("INSERT INTO student_project(id,student_id,name,description) VALUES(1,1,'keep','original unsplit text')");
  }
  assertThatThrownBy(()->Flyway.configure().dataSource(url,"sa","").target("7").load().migrate()).isInstanceOf(Exception.class);
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
   try(var rs=s.executeQuery("SELECT description FROM student_project")){rs.next();assertThat(rs.getString(1)).isEqualTo("original unsplit text");}
   assertThatThrownBy(()->s.executeQuery("SELECT overview FROM student_project")).isInstanceOf(SQLException.class);
  }
 }
}

