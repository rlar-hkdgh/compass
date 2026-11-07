package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Isolated temporary PostgreSQL only; never reads application configuration or .env. */
class OnboardingV13MigrationTests {
    static EmbeddedPostgres pg;
    @BeforeAll static void start() throws Exception {
        pg=EmbeddedPostgres.builder().setPort(0).start();
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()) {s.execute("CREATE ROLE anon");s.execute("CREATE ROLE authenticated");}
    }
    @AfterAll static void stop() throws Exception {if(pg!=null)pg.close();}
    String v12() throws Exception {
        String name="onboarding_"+UUID.randomUUID().toString().replace("-","");
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        String url=pg.getJdbcUrl("postgres",name);
        Flyway.configure().dataSource(url,"postgres","").target("12").load().migrate();return url;
    }
    Flyway v13(String url) {return Flyway.configure().dataSource(url,"postgres","").target("13").load();}
    void student(Statement s,long id) throws Exception {s.execute("insert into student(id,login_id,password_hash,school_email,name,is_transfer) values("+id+",'migration_user','noncredential-fixture','migration@sju.ac.kr','existing student',false)");}
    Map<String,String> snapshot(Connection c) throws Exception {
        List<String> tables=new ArrayList<>();
        try(var s=c.createStatement();var r=s.executeQuery("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','student_onboarding_step') order by tablename")){while(r.next())tables.add(r.getString(1));}
        Map<String,String> result=new TreeMap<>();
        for(String table:tables)try(var s=c.createStatement();var r=s.executeQuery("select coalesce(string_agg(row_to_json(t)::text,E'\\n' order by row_to_json(t)::text),'') from public.\""+table+"\" t")){r.next();result.put(table,r.getString(1));}
        return result;
    }
    @Test void additiveMigrationPreservesEveryExistingBusinessRowAndDoesNotBackfillProgress() throws Exception {
        String url=v12();Map<String,String> before;
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
            student(s,101);
            s.execute("insert into student_course(student_id,course_number,course_name,academic_year,semester,evaluation_method,grade,grade_point) values(101,'001234','existing course',2025,'1학기','P/NP','P',NULL)");
            s.execute("insert into student_project(student_id,name,overview,description) values(101,'existing project','existing overview','existing contribution')");
            before=snapshot(c);
        }
        var migration=v13(url);assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();assertThat(migration.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
            assertThat(snapshot(c)).isEqualTo(before);
            try(var r=s.executeQuery("select count(*) from student_onboarding_step")){r.next();assertThat(r.getInt(1)).isZero();}
            try(var r=s.executeQuery("select column_name from information_schema.columns where table_schema='public' and table_name='student_onboarding_step' order by ordinal_position")){List<String> columns=new ArrayList<>();while(r.next())columns.add(r.getString(1));assertThat(columns).containsExactly("student_id","step_code","status");}
        }
    }
    @Test void compositeKeyForeignKeyAndEnumeratedChecksProtectProgressRows() throws Exception {
        String url=v12();v13(url).migrate();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
            student(s,101);s.execute("insert into student_onboarding_step values(101,'BASIC_INFO','COMPLETED')");
            assertThatThrownBy(()->s.execute("insert into student_onboarding_step values(101,'BASIC_INFO','SKIPPED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into student_onboarding_step values(999,'MAJORS','SKIPPED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into student_onboarding_step values(101,'UNKNOWN','COMPLETED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into student_onboarding_step values(101,'MAJORS','NOT_STARTED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into student_onboarding_step values(101,'MAJORS',NULL)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("delete from student where id=101")).isInstanceOf(SQLException.class);
        }
    }
    @Test void rlsEnabledAndDirectApiPrivilegesRevokedEvenWithLegacyDefaultGrants() throws Exception {
        String url=v12();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated");}
        v13(url).migrate();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
            try(var r=s.executeQuery("select relrowsecurity from pg_class where oid='public.student_onboarding_step'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();}
            try(var r=s.executeQuery("select count(*) from pg_policies where schemaname='public' and tablename='student_onboarding_step'")){r.next();assertThat(r.getInt(1)).isZero();}
            for(String role:List.of("anon","authenticated"))for(String permission:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE")) {
                try(var q=c.prepareStatement("select has_table_privilege(?,'public.student_onboarding_step',?)")){q.setString(1,role);q.setString(2,permission);try(var r=q.executeQuery()){r.next();assertThat(r.getBoolean(1)).as(role+" "+permission).isFalse();}}
            }
            s.execute("SET ROLE anon");
            try{assertThatThrownBy(()->s.executeQuery("select * from student_onboarding_step")).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));}finally{s.execute("RESET ROLE");}
        }
    }
}
