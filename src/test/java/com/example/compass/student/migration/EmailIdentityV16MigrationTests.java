package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EmailIdentityV16MigrationTests {
    @Test void upgradePreservesEveryExistingRowAndHistoryAndProtectsNewPrivateData()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");
            Flyway.configure().dataSource(url,"postgres","").target("15").load().migrate();
            try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
                s.execute("create role anon");s.execute("create role authenticated");s.execute("create role service_role");
                s.execute("alter default privileges in schema public grant all on tables to anon,authenticated,service_role");
                s.execute("insert into student(id,login_id,password_hash,school_email,name,is_transfer) values(101,'legacy_user','original-hash','existing@sju.ac.kr','기존 학생',false)");
                s.execute("insert into student_onboarding_step values(101,'BASIC_INFO','COMPLETED')");
                s.execute("insert into student_email_verification(student_id,school_email,verified_at,last_sent_at,window_started_at,send_count,failed_attempts) values(101,'existing@sju.ac.kr',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,0)");
                s.execute("insert into signup_email_challenge(school_email) values('pending@sju.ac.kr')");
            }
            Map<String,String> before;
            try(var c=pg.getPostgresDatabase().getConnection()){before=snapshot(c);}
            var f=Flyway.configure().dataSource(url,"postgres","").target("16").load();
            assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
            try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
                assertThat(snapshot(c)).isEqualTo(before);
                try(var r=s.executeQuery("select count(*) from student_career_goal")){r.next();assertThat(r.getInt(1)).isZero();}
                try(var r=s.executeQuery("select student_number,grade_year,semester,gpa,gpa_scale from student where id=101")){
                    r.next();for(int i=1;i<=5;i++)assertThat(r.getObject(i)).isNull();
                }
                try(var r=s.executeQuery("select count(*) from flyway_schema_history where success=true and version in ('13','14','15','16')")){r.next();assertThat(r.getInt(1)).isEqualTo(4);}
                try(var r=s.executeQuery("select relrowsecurity from pg_class where oid='public.student_career_goal'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();}
                for(String role:List.of("anon","authenticated","service_role"))for(String privilege:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE")){
                    try(var q=c.prepareStatement("select has_table_privilege(?,'public.student_career_goal',?)")){
                        q.setString(1,role);q.setString(2,privilege);try(var r=q.executeQuery()){r.next();assertThat(r.getBoolean(1)).isFalse();}
                    }
                }
                s.execute("insert into student(id,password_hash,school_email,name,is_transfer) values(102,'hash','new@sju.ac.kr','신규',false),(103,'hash','another@sju.ac.kr','신규2',false)");
                s.execute("update student set student_number='20211234',grade_year=3,semester=6,gpa=3.85,gpa_scale=4.5 where id=101");
                for(String sql:List.of("update student set student_number='20211234' where id=102",
                    "update student set student_number='1234abcd' where id=102","update student set grade_year=5 where id=101",
                    "update student set semester=9 where id=101","update student set gpa=4.6 where id=101",
                    "update student set gpa_scale=null where id=101"))
                    assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class);
                s.execute("insert into student_career_goal values(101,'BACKEND','LARGE',5000,'NEW','기업','직무',DATE '2027-06-01')");
                for(String sql:List.of("update student_career_goal set target_date=DATE '2027-06-02' where student_id=101",
                    "update student_career_goal set target_date=DATE '2101-01-01' where student_id=101",
                    "update student_career_goal set job_field='UNKNOWN' where student_id=101",
                    "update student_career_goal set target_salary=-1 where student_id=101"))
                    assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class);
            }
        }
    }
    Map<String,String> snapshot(Connection c)throws Exception{
        var tables=new ArrayList<String>();
        try(var s=c.createStatement();var r=s.executeQuery("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','student_career_goal')")){
            while(r.next())tables.add(r.getString(1));
        }
        var result=new TreeMap<String,String>();
        for(String table:tables){
            String from=table.equals("student")?"(select id,login_id,password_hash,school_email,name,is_transfer from student)":"public.\""+table+"\"";
            try(var s=c.createStatement();var r=s.executeQuery("select coalesce(string_agg(row_to_json(t)::text,'' order by row_to_json(t)::text),'') from "+from+" t")){
                r.next();result.put(table,r.getString(1));
            }
        }
        return result;
    }
}
