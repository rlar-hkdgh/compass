package db.migration;
import org.flywaydb.core.api.migration.*;
import java.util.List;
/** Additive only: no existing business rows or migrations are changed. */
public class V14__school_email_verification extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 14001; }
    @Override public void migrate(Context context) throws Exception {
        var c=context.getConnection();
        try(var s=c.createStatement()) {
            s.execute("""
                CREATE TABLE student_email_verification (
                    student_id BIGINT PRIMARY KEY REFERENCES student(id) ON DELETE RESTRICT,
                    school_email VARCHAR(254) NOT NULL,
                    verified_at TIMESTAMP WITH TIME ZONE,
                    code_hash VARCHAR(255),
                    expires_at TIMESTAMP WITH TIME ZONE,
                    last_sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    send_count INTEGER NOT NULL CHECK(send_count BETWEEN 1 AND 10),
                    failed_attempts INTEGER NOT NULL CHECK(failed_attempts BETWEEN 0 AND 5),
                    CHECK((code_hash IS NULL AND expires_at IS NULL) OR
                          (code_hash IS NOT NULL AND expires_at IS NOT NULL)),
                    CHECK(verified_at IS NULL OR code_hash IS NULL)
                )
                """);
            if("PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())) {
                s.execute("ALTER TABLE student_email_verification ENABLE ROW LEVEL SECURITY");
                s.execute("REVOKE ALL ON TABLE student_email_verification FROM PUBLIC");
                for(String role:List.of("anon","authenticated","service_role")) {
                    try(var q=c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
                        q.setString(1,role);
                        try(var r=q.executeQuery()) { if(r.next()) s.execute("REVOKE ALL ON TABLE student_email_verification FROM \""+role+"\""); }
                    }
                }
            }
        }
    }
}
