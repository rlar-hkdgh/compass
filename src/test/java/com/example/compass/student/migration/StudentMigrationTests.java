package com.example.compass.student.migration;


import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StudentMigrationTests {
    private String database() {
        String url="jdbc:h2:mem:student-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        return url;
    }
    private void student(Statement s,long id,String login,String email) throws SQLException {
        s.executeUpdate("INSERT INTO student(id,login_id,password_hash,school_email,name,is_transfer) VALUES("+id+",'"+login+"','stored-hash','"+email+"','학생',false)");
    }
    @Test void initializesCurrentSchemaAndApprovedCatalogs() throws Exception {
        String url=database();
        var flyway=Flyway.configure().dataSource(url,"sa","").load();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            for(String table : new String[]{"major","certification"}) {
                try(var result=s.executeQuery("SELECT count(*) FROM "+table)) { result.next(); assertThat(result.getInt(1)).isEqualTo(table.equals("major") ? 62 : 9); }
            }
            try(var result=s.executeQuery("SELECT count(*) FROM major_completion_type")) { result.next(); assertThat(result.getInt(1)).isEqualTo(6); }
        }
    }
    @Test void enforcesUniqueAccountAndSchoolDomain() throws Exception {
        try(var c=DriverManager.getConnection(database(),"sa","");var s=c.createStatement()) {
            student(s,1,"first","first@sju.ac.kr");
            assertThatThrownBy(()->student(s,2,"first","other@sju.ac.kr")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->student(s,3,"other","first@sju.ac.kr")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->student(s,4,"outsider","outsider@example.com")).isInstanceOf(SQLException.class);
            assertThat(s.executeUpdate("UPDATE student SET login_id='changed' WHERE id=1")).isEqualTo(1);
        }
    }
    @Test void allowsSeveralMajorsAndRepeatedInternshipsButOneCurrentCertificate() throws Exception {
        try(var c=DriverManager.getConnection(database(),"sa","");var s=c.createStatement()) {
            student(s,1,"first","first@sejong.co.kr");
            s.executeUpdate("INSERT INTO major(code,name) VALUES('TEST1','테스트전공1'),('TEST2','테스트전공2'),('TEST3','테스트전공3')");
            s.executeUpdate("INSERT INTO student_major VALUES(1,'TEST1','PRIMARY'),(1,'TEST2','DOUBLE'),(1,'TEST3','DOUBLE')");
            s.executeUpdate("INSERT INTO student_internship(student_id,company_name,job_category_code,started_on,ended_on,description) VALUES(1,'같은회사','84',DATE '2025-01-01',DATE '2025-02-01','첫 경험'),(1,'같은회사','84',DATE '2025-03-01',DATE '2025-04-01','다른 경험')");
            s.executeUpdate("INSERT INTO certification(code,name,issuing_organization) VALUES('TEST','테스트자격증','테스트기관')");
            s.executeUpdate("INSERT INTO student_certification(student_id,certification_code) VALUES(1,'TEST')");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_certification(student_id,certification_code) VALUES(1,'TEST')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_certification(student_id,certification_code) VALUES(1,'UNKNOWN')")).isInstanceOf(SQLException.class);
        }
    }
    @Test void keepsRetakeRecordsAndPassGradesAndSharesRecruitCodes() throws Exception {
        try(var c=DriverManager.getConnection(database(),"sa","");var s=c.createStatement()) {
            student(s,1,"first","first@sju.ac.kr");
            s.executeUpdate("INSERT INTO student_course(student_id,course_number,course_name,academic_year,semester,evaluation_method,grade,grade_point) VALUES(1,'001234','수업',2025,'1학기','GRADE','D+',1.5),(1,'001234','수업',2026,'1학기','GRADE','A+',4.5),(1,'000001','봉사',2026,'2학기','P/NP','P',NULL)");
            s.executeUpdate("INSERT INTO student_preferred_job_category VALUES(1,'84')");
            s.executeUpdate("INSERT INTO student_salary_preference VALUES(1,'MONTHLY',3000000)");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO student_preferred_job_category VALUES(1,'UNKNOWN')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("UPDATE student_salary_preference SET min_amount=-1")).isInstanceOf(SQLException.class);
            try(var r=s.executeQuery("SELECT course_number FROM student_course WHERE grade='A+'")) {r.next(); assertThat(r.getString(1)).isEqualTo("001234");}
        }
    }
}
