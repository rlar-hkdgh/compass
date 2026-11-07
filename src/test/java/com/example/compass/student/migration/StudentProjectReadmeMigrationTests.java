package com.example.compass.student.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StudentProjectReadmeMigrationTests {
    @Test void upgradesV2PreservesExistingRowsAndEnforcesSnapshotAndReferenceRules() throws Exception {
        String url="jdbc:h2:mem:readme-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES(1,'test_user','hash','test@sju.ac.kr','student',false)");
            s.executeUpdate("INSERT INTO student_project(id,student_id,name,in_progress,role,description,github_url) VALUES(1,1,'project',true,'developer','student description','https://github.com/example/repo')");
            s.executeUpdate("INSERT INTO company(id,name) VALUES(1,'company')");
            s.executeUpdate("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,source_url,active,description,source_metadata) VALUES('SARAMIN','keep',1,'posting','https://example.com',true,'body','{}')");
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").target("3").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var row=s.executeQuery("SELECT description,github_url FROM student_project WHERE id=1")) {
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("student description");assertThat(row.getString(2)).isEqualTo("https://github.com/example/repo");
            }
            try(var row=s.executeQuery("SELECT description,source_metadata FROM job_posting WHERE source_posting_id='keep'")) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("body");assertThat(row.getString(2)).isEqualTo("{}");}
            try(var row=s.executeQuery("SELECT count(*) FROM student_project_readme")) {row.next();assertThat(row.getInt(1)).isZero();}
            String insert="INSERT INTO student_project_readme(project_id,repository_url,last_attempt_at,last_attempt_status) VALUES(%d,'https://github.com/example/repo',CURRENT_TIMESTAMP,'%s')";
            assertThatThrownBy(()->s.executeUpdate(insert.formatted(999,"FAILED"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate(insert.formatted(1,"UNKNOWN"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate(insert.formatted(1,"SUCCESS"))).isInstanceOf(SQLException.class);
            s.executeUpdate(insert.formatted(1,"NOT_FOUND"));
            assertThatThrownBy(()->s.executeUpdate(insert.formatted(1,"FAILED"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("DELETE FROM student_project WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("UPDATE student_project_readme SET content='partial' WHERE project_id=1")).isInstanceOf(SQLException.class);
            s.executeUpdate("UPDATE student_project_readme SET content='',readme_path='README.md',readme_sha='file-sha',last_success_at=CURRENT_TIMESTAMP,last_attempt_status='SUCCESS' WHERE project_id=1");
            assertThatThrownBy(()->s.executeUpdate("UPDATE student_project_readme SET last_error_code='ERROR' WHERE project_id=1")).isInstanceOf(SQLException.class);
            s.executeUpdate("UPDATE student_project_readme SET last_attempt_status='RATE_LIMITED',last_error_code='GITHUB_RATE_LIMIT' WHERE project_id=1");
            s.executeUpdate("UPDATE student_project_readme SET last_attempt_status='FAILED' WHERE project_id=1");
            try(var row=s.executeQuery("SELECT content,readme_sha FROM student_project_readme WHERE project_id=1")) {row.next();assertThat(row.getString(1)).isEmpty();assertThat(row.getString(2)).isEqualTo("file-sha");}
        }
    }
}
