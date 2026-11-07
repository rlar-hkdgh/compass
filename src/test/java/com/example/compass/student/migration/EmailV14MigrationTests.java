package com.example.compass.student.migration;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/** Temporary local PostgreSQL only; never loads application configuration or .env. */
class EmailV14MigrationTests {
    static EmbeddedPostgres pg;
    @BeforeAll static void start() throws Exception {
        pg=EmbeddedPostgres.builder().setPort(0).start();
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
            for(String role:List.of("anon","authenticated","service_role"))s.execute("CREATE ROLE "+role);
        }
    }
    @AfterAll static void stop() throws Exception {if(pg!=null)pg.close();}
    String v13() throws Exception {
        String name="email_"+UUID.randomUUID().toString().replace("-","");
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        String url=pg.getJdbcUrl("postgres",name);
        Flyway.configure().dataSource(url,"postgres","").target("13").load().migrate();return url;
    }
    Flyway v14(String url){return Flyway.configure().dataSource(url,"postgres","").target("14").load();}
    Map<String,String> snapshot(Connection c) throws Exception {
        List<String> tables=new ArrayList<>();
        try(var s=c.createStatement();var r=s.executeQuery("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','student_email_verification') order by tablename")){while(r.next())tables.add(r.getString(1));}
        Map<String,String> result=new TreeMap<>();
        for(String table:tables)try(var s=c.createStatement();var r=s.executeQuery("select coalesce(string_agg(row_to_json(t)::text,E'\\n' order by row_to_json(t)::text),'') from public.\""+table+"\" t")){r.next();result.put(table,r.getString(1));}
        return result;
    }
    @Test void upgradePreservesAllExistingRowsDoesNotInferVerificationAndIsRepeatable() throws Exception {
        String url=v13();Map<String,String> before;
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            s.execute("insert into student(id,login_id,password_hash,school_email,name,is_transfer) values(101,'existing_user','noncredential-fixture','existing@sju.ac.kr','기존 사용자',false)");
            s.execute("insert into student_onboarding_step values(101,'BASIC_INFO','COMPLETED')");
            before=snapshot(c);
        }
        var f=v14(url);assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            assertThat(snapshot(c)).isEqualTo(before);
            try(var r=s.executeQuery("select count(*) from student_email_verification")){r.next();assertThat(r.getInt(1)).isZero();}
        }
    }
    @Test void rlsAndRevokesBlockDirectDataApiEvenWithLegacyDefaultGrants() throws Exception {
        String url=v13();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role");
        }
        v14(url).migrate();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            try(var r=s.executeQuery("select relrowsecurity from pg_class where oid='public.student_email_verification'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();}
            for(String role:List.of("anon","authenticated","service_role"))for(String permission:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE")){
                try(var q=c.prepareStatement("select has_table_privilege(?,'public.student_email_verification',?)")){
                    q.setString(1,role);q.setString(2,permission);try(var r=q.executeQuery()){r.next();assertThat(r.getBoolean(1)).as(role+" "+permission).isFalse();}
                }
            }
            s.execute("SET ROLE anon");
            try{assertThatThrownBy(()->s.executeQuery("select * from student_email_verification")).isInstanceOf(SQLException.class);}finally{s.execute("RESET ROLE");}
        }
    }
    @Test void foreignKeyAndQuotaChecksProtectVerificationRows() throws Exception {
        String url=v13();v14(url).migrate();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            s.execute("insert into student(id,login_id,password_hash,school_email,name,is_transfer) values(101,'existing_user','noncredential-fixture','existing@sju.ac.kr','기존 사용자',false)");
            s.execute("insert into student_email_verification(student_id,school_email,last_sent_at,window_started_at,send_count,failed_attempts) values(101,'existing@sju.ac.kr',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,0)");
            assertThatThrownBy(()->s.execute("update student_email_verification set send_count=11")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update student_email_verification set failed_attempts=6")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update student_email_verification set code_hash='hash'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update student_email_verification set student_id=999")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("delete from student where id=101")).isInstanceOf(SQLException.class);
        }
    }
}
