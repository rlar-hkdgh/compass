package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CareerGoalV17MigrationTests {
    @Test
    void mapsDirectMatchesAndKeepsAmbiguousGoalsForReselection() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            Flyway.configure().dataSource(url, "postgres", "").target("16").load().migrate();
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                statement.execute("""
                    insert into student(id,password_hash,school_email,name,is_transfer)
                    values (101,'hash','backend@sju.ac.kr','백엔드',false),
                           (102,'hash','frontend@sju.ac.kr','프론트엔드',false),
                           (103,'hash','data@sju.ac.kr','데이터',false)
                    """);
                statement.execute("""
                    insert into student_career_goal values
                    (101,'BACKEND','LARGE',5000,'NEW','기업','직무',DATE '2027-06-01'),
                    (102,'FRONTEND','MID',4200,'NEW','기업','직무',DATE '2027-07-01'),
                    (103,'DATA','STARTUP',4000,'NEW','데이터 기업','분석',DATE '2027-08-01')
                    """);
                statement.execute("""
                    insert into student_onboarding_step values
                    (101,'PREFERENCES','COMPLETED'), (102,'PREFERENCES','COMPLETED'),
                    (103,'PREFERENCES','COMPLETED'), (103,'COURSES','SKIPPED')
                    """);
            }
            var flyway = Flyway.configure().dataSource(url, "postgres", "").target("17").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                Map<String,String> classifications = new LinkedHashMap<>();
                try (var rows = statement.executeQuery("select job_field,job_category_code from student_career_goal")) {
                    while (rows.next()) classifications.put(rows.getString(1), rows.getString(2));
                }
                assertThat(classifications).hasSize(3).containsEntry("BACKEND", "84")
                    .containsEntry("FRONTEND", "92").containsEntry("DATA", null);
                try (var row = statement.executeQuery("select target_company,target_salary,target_role,target_date from student_career_goal where student_id=103")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("target_company")).isEqualTo("데이터 기업");
                    assertThat(row.getLong("target_salary")).isEqualTo(4000);
                    assertThat(row.getString("target_role")).isEqualTo("분석");
                    assertThat(row.getDate("target_date").toLocalDate()).isEqualTo(java.time.LocalDate.of(2027,8,1));
                }
                try (var rows = statement.executeQuery("select student_id,step_code,status from student_onboarding_step order by student_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("student_id")).isEqualTo(101);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("student_id")).isEqualTo(102);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("student_id")).isEqualTo(103);
                    assertThat(rows.getString("step_code")).isEqualTo("COURSES");
                    assertThat(rows.getString("status")).isEqualTo("SKIPPED");
                    assertThat(rows.next()).isFalse();
                }
                assertThatThrownBy(() -> statement.execute("update student_career_goal set job_category_code='missing' where student_id=103"))
                    .isInstanceOf(SQLException.class);
            }
        }
    }
}
