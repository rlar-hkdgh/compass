package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class CareerGoalV18MigrationTests {
    @Test void keepsLegacyGoalAndStoresItsCompanySizeAsOneSelection() throws Exception {
        try (var pg=EmbeddedPostgres.builder().setPort(0).start()) {
            String url=pg.getJdbcUrl("postgres","postgres");
            Flyway.configure().dataSource(url,"postgres","").target("17").load().migrate();
            try (var connection=pg.getPostgresDatabase().getConnection();var s=connection.createStatement()) {
                s.execute("create role anon");
                s.execute("alter default privileges in schema public grant all on tables to anon");
                s.execute("insert into student(id,password_hash,school_email,name,is_transfer) values(101,'fixture','old@sju.ac.kr','학생',false)");
                s.execute("""
                    insert into student_career_goal(student_id,job_category_code,company_size,target_salary,career_type,target_company,target_role,target_date)
                    values(101,'92','LARGE',5000,'NEW','기존 기업','기존 직무명',DATE '2027-06-01')
                    """);
                s.execute("insert into student_onboarding_step values(101,'PREFERENCES','COMPLETED')");
            }
            var migration=Flyway.configure().dataSource(url,"postgres","").target("18").load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            migration.validate();
            try (var connection=pg.getPostgresDatabase().getConnection();var s=connection.createStatement()) {
                try (var r=s.executeQuery("""
                    select g.target_company,g.career_type,g.target_role,g.target_salary,s.company_size,o.status
                    from student_career_goal g join student_career_goal_company_size s using(student_id)
                    join student_onboarding_step o using(student_id) where g.student_id=101
                    """)) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getString("target_company")).isEqualTo("기존 기업");
                    assertThat(r.getString("career_type")).isEqualTo("NEW");
                    assertThat(r.getString("target_role")).isEqualTo("기존 직무명");
                    assertThat(r.getLong("target_salary")).isEqualTo(5000);
                    assertThat(r.getString("company_size")).isEqualTo("LARGE");
                    assertThat(r.getString("status")).isEqualTo("COMPLETED");
                    assertThat(r.next()).isFalse();
                }
                try (var r=s.executeQuery("""
                    select relrowsecurity,has_table_privilege('anon','public.student_career_goal_company_size','SELECT')
                    from pg_class where oid='public.student_career_goal_company_size'::regclass
                    """)) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getBoolean(1)).isTrue();
                    assertThat(r.getBoolean(2)).isFalse();
                }
                assertThat(s.executeUpdate("insert into student_career_goal_company_size values(101,'SMALL')")).isEqualTo(1);
                assertThatThrownBy(() -> s.execute("insert into student_career_goal_company_size values(101,'SMALL')"))
                    .isInstanceOf(SQLException.class);
                s.execute("delete from student_career_goal where student_id=101");
                try (var r=s.executeQuery("select count(*) from student_career_goal_company_size where student_id=101")) {
                    r.next();
                    assertThat(r.getInt(1)).isZero();
                }
            }
        }
    }
}
