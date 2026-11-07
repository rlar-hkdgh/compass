package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class StudentInputV20MigrationTests {
    @Test void requiresResolvedJobAndPreservesAccountGoalAndCompanySelections() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            Flyway.configure().dataSource(url, "postgres", "").target("19").load().migrate();
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                statement.execute("""
                    insert into student(id,login_id,student_number,password_hash,school_email,name,is_transfer,semester)
                    values (101,'fixture','20221234','test-hash','first@sju.ac.kr','학생',false,7),
                           (102,'fixture2','20221235','test-hash','second@sju.ac.kr','학생',false,5)
                    """);
                statement.execute("""
                    insert into student_career_goal(student_id,job_field,job_category_code,company_size,
                        target_salary,career_type,target_company,target_role,target_date)
                    values (101,'DATA',null,'MID',5000,'NEW','기업','분석',date '2027-06-01'),
                           (102,'BACKEND','84','LARGE',6000,'NEW','다른 기업','개발',date '2027-07-01')
                    """);
                statement.execute("insert into student_career_goal_company_size values (102,'LARGE'),(102,'SMALL')");
                var migration = Flyway.configure().dataSource(url, "postgres", "").target("20").load();
                assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class).hasStackTraceContaining("job_category_code");
                assertThat(statement.executeUpdate("update student_career_goal set job_category_code='82' where job_field='DATA'"))
                    .isEqualTo(1);
                assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
                migration.validate();
                try (var rows = statement.executeQuery("""
                    select s.school_email,s.password_hash,s.semester,g.job_category_code,g.target_company,g.target_salary,
                           (select string_agg(company_size,',' order by company_size)
                            from student_career_goal_company_size where student_id=s.id) sizes
                    from student s join student_career_goal g on g.student_id=s.id order by s.id
                    """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("school_email")).isEqualTo("first@sju.ac.kr");
                    assertThat(rows.getString("password_hash")).isEqualTo("test-hash");
                    assertThat(rows.getInt("semester")).isEqualTo(7);
                    assertThat(rows.getString("job_category_code")).isEqualTo("82");
                    assertThat(rows.getString("target_company")).isEqualTo("기업");
                    assertThat(rows.getLong("target_salary")).isEqualTo(5000);
                    assertThat(rows.getString("sizes")).isEqualTo("MID");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("sizes")).isEqualTo("LARGE,SMALL");
                    assertThat(rows.next()).isFalse();
                }
                try (var rows = statement.executeQuery("""
                    select count(*) from information_schema.columns where table_schema='public' and
                    ((table_name='student' and column_name in ('login_id','student_number'))
                     or (table_name='student_career_goal' and column_name in ('job_field','career_type','target_role','company_size')))
                    """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                assertThatThrownBy(() -> statement.executeUpdate("update student_career_goal set job_category_code=null where student_id=101"))
                    .isInstanceOf(SQLException.class);
            }
        }
    }
}
