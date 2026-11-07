package com.example.compass.student.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StudentV5MigrationTests {
    private String v4() {
        String url="jdbc:h2:mem:student-v5-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("4").load().migrate();return url;
    }
    @Test void upgradesToFifteenStudentTablesWithApprovedSeedsAndPreservesUnrelatedData() throws Exception {
        String url=v4();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO company(id,name) VALUES(1,'keep')");
            s.executeUpdate("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','keep',1,'keep',true)");
            s.executeUpdate("INSERT INTO job_posting_detail VALUES('SARAMIN','keep','https://example.com','Java','keep body')");
            s.executeUpdate("INSERT INTO job_posting_collection_metadata VALUES('SARAMIN','keep','{\"keep\":true}')");
            s.executeUpdate("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'keep_user','hash','keep@sju.ac.kr','keep',false)");
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").target("5").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var row=s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='PUBLIC' AND (table_name LIKE 'STUDENT%' OR table_name IN ('MAJOR','MAJOR_COMPLETION_TYPE','CERTIFICATION'))")) {row.next();assertThat(row.getInt(1)).isEqualTo(15);}
            for(String[] pair:new String[][]{{"major","62"},{"certification","9"},{"major_completion_type","6"}}) {
                try(var row=s.executeQuery("SELECT count(*) FROM "+pair[0])) {row.next();assertThat(row.getInt(1)).isEqualTo(Integer.parseInt(pair[1]));}
            }
            try(var row=s.executeQuery("SELECT name,college_name FROM major WHERE code='3513'")) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("컴퓨터공학과");assertThat(row.getString(2)).isEqualTo("인공지능융합대학");}
            try(var row=s.executeQuery("SELECT count(*) FROM major WHERE code='3210'")) {row.next();assertThat(row.getInt(1)).isZero();}
            try(var row=s.executeQuery("SELECT d.description,m.source_metadata FROM job_posting_detail d JOIN job_posting_collection_metadata m USING(source_code,source_posting_id)")) {row.next();assertThat(row.getString(1)).isEqualTo("keep body");assertThat(row.getString(2)).isEqualTo("{\"keep\":true}");}
            try(var row=s.executeQuery("SELECT login_id FROM student WHERE id=1")) {row.next();assertThat(row.getString(1)).isEqualTo("keep_user");}
            s.executeUpdate("INSERT INTO student_project(student_id,name,description) VALUES(1,'completed','role + work + outcome')");
            s.executeUpdate("INSERT INTO student_award(student_id,name,organizer) VALUES(1,'award','organizer')");
            s.executeUpdate("INSERT INTO student_certification VALUES(1,'QNET_1320')");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_certification VALUES(1,'QNET_1320')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_certification VALUES(1,'UNKNOWN')")).isInstanceOf(SQLException.class);
            s.executeUpdate("INSERT INTO student_internship(student_id,company_name,job_category_code,started_on,ended_on,description) VALUES(1,'company','84',DATE '2025-01-01',DATE '2025-02-01','work + outcome')");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_internship(student_id,company_name,job_category_code,started_on,ended_on,description) VALUES(1,'company','UNKNOWN',DATE '2025-01-01',DATE '2025-02-01','work')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("UPDATE student_internship SET ended_on=DATE '2024-01-01'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeQuery("SELECT role FROM student_project")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeQuery("SELECT expected_graduation_year FROM student")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeQuery("SELECT * FROM student_project_tech_stack")).isInstanceOf(SQLException.class);
        }
    }
    @Test void stopsBeforeDroppingTablesWhenAnActivityNeedsConversion() throws Exception {
        String url=v4();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'keep_user','hash','keep@sju.ac.kr','keep',false)");
            s.executeUpdate("INSERT INTO student_project(student_id,name,in_progress,role,description,outcome) VALUES(1,'keep',false,'original role','original work','original outcome')");
        }
        assertThatThrownBy(()->Flyway.configure().dataSource(url,"sa","").target("5").load().migrate()).isInstanceOf(Exception.class);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var row=s.executeQuery("SELECT role,description,outcome FROM student_project")) {
            assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("original role");assertThat(row.getString(2)).isEqualTo("original work");assertThat(row.getString(3)).isEqualTo("original outcome");
        }
    }
}
