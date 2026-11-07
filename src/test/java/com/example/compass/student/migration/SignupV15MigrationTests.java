package com.example.compass.student.migration;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SignupV15MigrationTests {
 @Test void additiveUpgradePreservesExistingDataAndHistoryAndRevokesDirectApiAccess()throws Exception{
  try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
   String url=pg.getJdbcUrl("postgres","postgres");
   Flyway.configure().dataSource(url,"postgres","").target("14").load().migrate();
   try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
    s.execute("create role anon");s.execute("create role authenticated");s.execute("create role service_role");
    s.execute("alter default privileges in schema public grant all on tables to anon,authenticated,service_role");
    s.execute("insert into student(id,login_id,password_hash,school_email,name,is_transfer) values(101,'existing_user','fixture','existing@sju.ac.kr','기존',false)");
    s.execute("insert into student_onboarding_step values(101,'BASIC_INFO','COMPLETED')");
    s.execute("insert into student_email_verification(student_id,school_email,verified_at,last_sent_at,window_started_at,send_count,failed_attempts) values(101,'existing@sju.ac.kr',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,0)");
   }
   Map<String,String> before;
   try(var c=pg.getPostgresDatabase().getConnection()){before=snapshot(c);}
   var f=Flyway.configure().dataSource(url,"postgres","").target("15").load();
   assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
   try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
    assertThat(snapshot(c)).isEqualTo(before);
    try(var r=s.executeQuery("select count(*) from signup_email_challenge")){r.next();assertThat(r.getInt(1)).isZero();}
    try(var r=s.executeQuery("select relrowsecurity from pg_class where oid='public.signup_email_challenge'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();}
    for(String role:List.of("anon","authenticated","service_role"))for(String privilege:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE")){
     try(var q=c.prepareStatement("select has_table_privilege(?,'public.signup_email_challenge',?)")){q.setString(1,role);q.setString(2,privilege);try(var r=q.executeQuery()){r.next();assertThat(r.getBoolean(1)).isFalse();}}
    }
    s.execute("insert into signup_email_challenge(school_email) values('pending@sju.ac.kr')");
    assertThatThrownBy(()->s.execute("update signup_email_challenge set failed_attempts=6")).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->s.execute("update signup_email_challenge set send_count=11")).isInstanceOf(SQLException.class);
   }
  }
 }
 Map<String,String> snapshot(Connection c)throws Exception{
  var tables=new ArrayList<String>();
  try(var s=c.createStatement();var r=s.executeQuery("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','signup_email_challenge')")){while(r.next())tables.add(r.getString(1));}
  var values=new TreeMap<String,String>();
  for(String t:tables)try(var s=c.createStatement();var r=s.executeQuery("select coalesce(string_agg(row_to_json(t)::text,'' order by row_to_json(t)::text),'') from public.\""+t+"\" t")){r.next();values.put(t,r.getString(1));}
  return values;
 }
}
