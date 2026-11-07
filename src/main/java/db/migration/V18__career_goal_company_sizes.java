package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.List;

public class V18__career_goal_company_sizes extends BaseJavaMigration {
    @Override
    public Integer getChecksum() {
        return 18001;
    }

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE student_career_goal_company_size (
                    student_id BIGINT NOT NULL REFERENCES student_career_goal(student_id) ON DELETE CASCADE,
                    company_size VARCHAR(16) NOT NULL CHECK (company_size IN ('LARGE','MID','STARTUP','SMALL')),
                    PRIMARY KEY (student_id, company_size)
                )
                """);
            statement.execute("""
                INSERT INTO student_career_goal_company_size (student_id, company_size)
                SELECT student_id, company_size FROM student_career_goal
                """);
            statement.execute("ALTER TABLE student_career_goal ALTER COLUMN company_size DROP NOT NULL");
            statement.execute("ALTER TABLE student_career_goal ALTER COLUMN career_type DROP NOT NULL");
            statement.execute("ALTER TABLE student_career_goal ALTER COLUMN target_role DROP NOT NULL");
            if ("PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                statement.execute("ALTER TABLE student_career_goal_company_size ENABLE ROW LEVEL SECURITY");
                statement.execute("REVOKE ALL ON TABLE student_career_goal_company_size FROM PUBLIC");
                for (String role : List.of("anon", "authenticated", "service_role")) {
                    try (var query = connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
                        query.setString(1, role);
                        try (var rows = query.executeQuery()) {
                            if (rows.next()) {
                                statement.execute("REVOKE ALL ON TABLE student_career_goal_company_size FROM " + role);
                            }
                        }
                    }
                }
            }
        }
    }
}
