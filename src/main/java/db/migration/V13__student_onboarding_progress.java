package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.List;

/** Additive only: no backfill, existing-row modification or inferred completion. */
public class V13__student_onboarding_progress extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 13001; }
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var statement=connection.createStatement()) {
            statement.execute("""
                CREATE TABLE student_onboarding_step (
                    student_id BIGINT NOT NULL,
                    step_code VARCHAR(32) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    CONSTRAINT pk_student_onboarding_step PRIMARY KEY(student_id,step_code),
                    CONSTRAINT fk_onboarding_student FOREIGN KEY(student_id) REFERENCES student(id) ON DELETE RESTRICT,
                    CONSTRAINT chk_onboarding_step CHECK(step_code IN
                        ('BASIC_INFO','MAJORS','PREFERENCES','COURSES','PROJECTS','INTERNSHIPS','AWARDS','CERTIFICATIONS')),
                    CONSTRAINT chk_onboarding_status CHECK(status IN ('COMPLETED','SKIPPED'))
                )
                """);
            if("PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                // Backend session authentication owns authorization; no direct Data API access is granted.
                statement.execute("ALTER TABLE student_onboarding_step ENABLE ROW LEVEL SECURITY");
                statement.execute("REVOKE ALL ON TABLE student_onboarding_step FROM PUBLIC");
                for(String role:List.of("anon","authenticated")) {
                    try(var query=connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
                        query.setString(1,role);
                        try(var rows=query.executeQuery()) {
                            if(rows.next()) statement.execute("REVOKE ALL ON TABLE student_onboarding_step FROM \""+role+"\"");
                        }
                    }
                }
            }
        }
    }
}
