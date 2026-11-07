package com.example.compass.student.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class StudentSemesterV19MigrationTests {
    @Test void removesGradeYearAndPreservesExistingCumulativeSemesters() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            Flyway.configure().dataSource(url, "postgres", "").target("18").load().migrate();
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                statement.execute("""
                    insert into student(id,password_hash,school_email,name,is_transfer,grade_year,semester)
                    values (101,'fixture','first@sju.ac.kr','학생',false,4,7),
                           (102,'fixture','second@sju.ac.kr','학생',false,3,null)
                    """);
            }
            var migration = Flyway.configure().dataSource(url, "postgres", "").target("19").load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            migration.validate();
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("select id, semester from student order by id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(101);
                    assertThat(rows.getInt("semester")).isEqualTo(7);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(102);
                    assertThat(rows.getObject("semester")).isNull();
                    assertThat(rows.next()).isFalse();
                }
                assertThatThrownBy(() -> statement.executeQuery("select grade_year from student"))
                    .isInstanceOf(SQLException.class);
                assertThat(statement.executeUpdate("update student set semester=9 where id=101")).isEqualTo(1);
                assertThat(statement.executeUpdate("update student set semester=0 where id=102")).isEqualTo(1);
                assertThatThrownBy(() -> statement.executeUpdate("update student set semester=-1 where id=101"))
                    .isInstanceOf(SQLException.class);
            }
        }
    }
}
