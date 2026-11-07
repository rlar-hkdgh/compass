package com.example.compass.activity;

import org.flywaydb.core.Flyway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ActivityV10MigrationTests {
    static EmbeddedPostgres pg;
    @BeforeAll static void start()throws Exception{pg=EmbeddedPostgres.builder().setPort(0).start();}
    @AfterAll static void stop()throws Exception{if(pg!=null)pg.close();}
    private String v9()throws Exception{
        String name="activity_final_"+UUID.randomUUID().toString().replace("-","");try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        String url=pg.getJdbcUrl("postgres",name);Flyway.configure().dataSource(url,"postgres","").target("9").load().migrate();return url;
    }
    private void seed(Statement s)throws Exception{
        s.execute("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'task_user','hash','task@sju.ac.kr','student',false)");
        s.execute("INSERT INTO company(id,name) VALUES(1,'company')");s.execute("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','target',1,'posting',true)");
        s.execute("INSERT INTO posting_analysis(id,source_code,source_posting_id,input_hash,input_snapshot,status,started_at) VALUES(1,'SARAMIN','target','hash','{}','SUCCESS',CURRENT_TIMESTAMP)");
        s.execute("INSERT INTO gap_analysis(id,student_id,posting_analysis_id,evaluation_policy_id,student_input_hash,student_input_snapshot,status,started_at) VALUES(1,1,1,1,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)");
        for(String[] item:new String[][]{{"10","COURSE"},{"20","EXTRACURRICULAR"},{"30","CERTIFICATION"}})
            s.execute("INSERT INTO career_activity(id,activity_type_code,source_code,source_activity_id,name,provider_name,description,delivery_mode,availability_status,is_active,created_at,updated_at) VALUES("+item[0]+",'"+item[1]+"','MANUAL','"+item[0]+"','name','provider','keep content','UNKNOWN','UNKNOWN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s.execute("INSERT INTO course_activity(activity_id,course_number,academic_year,semester,section_number,department_code,credits) VALUES(10,'001234',2026,'SECOND_SEMESTER','001','3513',3)");
        s.execute("INSERT INTO extracurricular_activity(activity_id,program_id,completion_condition) VALUES(20,'4346','keep condition')");
        s.execute("INSERT INTO certification_activity(activity_id,certification_code,exam_content) VALUES(30,'KDATA_SQLD','SQL exam content')");
        for(int id:new int[]{1,2}){
            s.execute("INSERT INTO career_roadmap(id,student_id,source_code,source_posting_id,gap_analysis_id,name,generation_status,created_at,updated_at) VALUES("+id+",1,'SARAMIN','target',1,'plan','SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO roadmap_period(id,career_roadmap_id,academic_year,period_type,display_order) VALUES("+id+","+id+",2026,'WINTER_BREAK',1)");
        }
        s.execute("INSERT INTO student_active_roadmap VALUES(1,'SARAMIN','target',1,CURRENT_TIMESTAMP)");
        int i=0;for(String[] item:new String[][]{{"10","COURSE"},{"20","EXTRACURRICULAR"},{"30","CERTIFICATION"}}){int id=100+i;s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,activity_type,activity_id,activity_snapshot,display_order,status,created_at,updated_at) VALUES("+id+",1,'current task','task content','"+item[1]+"',"+item[0]+",'{}',"+(++i)+",'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");}
        s.execute("INSERT INTO roadmap_task(id,roadmap_period_id,title,description,activity_type,display_order,status,created_at,updated_at) VALUES(200,2,'old history','discard previous plan','PROJECT',1,'TODO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s.execute("INSERT INTO certification_exam_schedule(id,certification_code,exam_year,round_number,exam_stage,starts_on,ends_on,source_url,checked_at) VALUES(1,'KDATA_SQLD',2026,60,'SINGLE',DATE '2026-03-07',DATE '2026-03-07','https://official.example/',CURRENT_TIMESTAMP)");
    }
    @Test void migratesActualPostgresAndPreservesCurrentLinksWhileDroppingHistory()throws Exception{
        String url=v9();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){seed(s);}
        var flyway=Flyway.configure().dataSource(url,"postgres","").target("10").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            for(String[] item:new String[][]{{"course_activity","11"},{"career_activity","12"},{"certification","4"},{"certification_exam_schedule","9"},{"recommended_task","14"}})
                try(var rs=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='"+item[0]+"'")){rs.next();assertThat(rs.getInt(1)).as(item[0]).isEqualTo(Integer.parseInt(item[1]));}
            try(var rs=s.executeQuery("SELECT course_number FROM course_activity WHERE id=10")){rs.next();assertThat(rs.getString(1)).isEqualTo("001234");}
            try(var rs=s.executeQuery("SELECT description FROM career_activity WHERE id=20")){rs.next();assertThat(rs.getString(1)).contains("keep content","keep condition");}
            try(var rs=s.executeQuery("SELECT exam_content FROM certification WHERE code='KDATA_SQLD'")){rs.next();assertThat(rs.getString(1)).contains("SQL exam content");}
            try(var rs=s.executeQuery("SELECT count(*) FROM recommended_task")){rs.next();assertThat(rs.getInt(1)).isEqualTo(3);}
            try(var rs=s.executeQuery("SELECT course_activity_id,career_activity_id,certification_code FROM recommended_task ORDER BY id")){rs.next();assertThat(rs.getLong(1)).isEqualTo(10);rs.next();assertThat(rs.getLong(2)).isEqualTo(20);rs.next();assertThat(rs.getString(3)).isEqualTo("KDATA_SQLD");}
            assertThatThrownBy(()->s.execute("SELECT * FROM career_roadmap")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("SELECT * FROM roadmap_period")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("SELECT * FROM certification_activity")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE recommended_task SET certification_code='KDATA_SQLD' WHERE id=100")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE recommended_task SET certification_exam_schedule_id=1 WHERE id=100")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE recommended_task SET status='COMPLETED' WHERE id=100")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("DELETE FROM course_activity WHERE id=10")).isInstanceOf(SQLException.class);
            s.execute("UPDATE certification SET exam_content='manual SQL content' WHERE code='KDATA_SQLD'");
            s.execute("INSERT INTO certification_exam_schedule(certification_code,exam_year,round_number,exam_stage,application_starts_on,application_ends_on,starts_on,ends_on) VALUES('KDATA_SQLD',2027,64,'SINGLE',DATE '2027-01-01',DATE '2027-01-10',DATE '2027-02-01',DATE '2027-02-01') ON CONFLICT(certification_code,exam_year,round_number,exam_stage) DO UPDATE SET starts_on=EXCLUDED.starts_on,ends_on=EXCLUDED.ends_on");
            s.execute("INSERT INTO career_activity(activity_type_code,source_code,name,provider_name,description) VALUES('BOOTCAMP','MANUAL','new','provider','body')");
            try(var rs=s.executeQuery("SELECT max(id) FROM career_activity")){rs.next();assertThat(rs.getLong(1)).isGreaterThan(20);}
            try(var rs=s.executeQuery("SELECT count(*) FROM gap_analysis")){rs.next();assertThat(rs.getInt(1)).isEqualTo(1);}
        }
    }
    @Test void refusesAmbiguousCurrentTargetsBeforeAnyDestructiveChange()throws Exception{
        String url=v9();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            seed(s);s.execute("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','other',1,'other posting',true)");s.execute("UPDATE career_roadmap SET source_posting_id='other' WHERE id=2");s.execute("INSERT INTO student_active_roadmap VALUES(1,'SARAMIN','other',2,CURRENT_TIMESTAMP)");
        }
        assertThatThrownBy(()->Flyway.configure().dataSource(url,"postgres","").target("10").load().migrate()).isInstanceOf(Exception.class);
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM career_roadmap")){rs.next();assertThat(rs.getInt(1)).isEqualTo(2);}
    }
}
