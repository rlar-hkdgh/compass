package db.migration;

import org.flywaydb.core.api.migration.*;
import java.util.List;

/** Keeps legacy login IDs and all V1-V15 data. New fields have no inferred backfill. */
public class V16__email_identity_and_student_career_goal extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 16001;}
    @Override public void migrate(Context context)throws Exception{
        var c=context.getConnection();
        try(var s=c.createStatement()){
            s.execute("ALTER TABLE student ALTER COLUMN login_id DROP NOT NULL");
            s.execute("ALTER TABLE student ADD COLUMN student_number VARCHAR(8)");
            s.execute("ALTER TABLE student ADD COLUMN grade_year INTEGER");
            s.execute("ALTER TABLE student ADD COLUMN semester INTEGER");
            s.execute("ALTER TABLE student ADD COLUMN gpa NUMERIC(3,2)");
            s.execute("ALTER TABLE student ADD COLUMN gpa_scale NUMERIC(3,2)");
            s.execute("ALTER TABLE student ADD CONSTRAINT uq_student_number UNIQUE(student_number)");
            s.execute("ALTER TABLE student ADD CONSTRAINT chk_student_number CHECK(student_number IS NULL OR student_number ~ '^[0-9]{8}$')");
            s.execute("ALTER TABLE student ADD CONSTRAINT chk_student_grade_year CHECK(grade_year BETWEEN 1 AND 4)");
            s.execute("ALTER TABLE student ADD CONSTRAINT chk_student_semester CHECK(semester BETWEEN 1 AND 8)");
            s.execute("""
                ALTER TABLE student ADD CONSTRAINT chk_student_gpa CHECK(
                    (gpa IS NULL AND gpa_scale IS NULL) OR
                    (gpa IS NOT NULL AND gpa_scale IS NOT NULL AND gpa>=0 AND gpa<=gpa_scale AND gpa_scale>0 AND gpa_scale<=4.5))
                """);
            s.execute("""
                CREATE TABLE student_career_goal (
                    student_id BIGINT PRIMARY KEY REFERENCES student(id) ON DELETE RESTRICT,
                    job_field VARCHAR(16) NOT NULL CHECK(job_field IN ('BACKEND','FRONTEND','DATA','AI','CLOUD','SECURITY')),
                    company_size VARCHAR(16) NOT NULL CHECK(company_size IN ('LARGE','MID','STARTUP')),
                    target_salary BIGINT NOT NULL CHECK(target_salary>=0),
                    career_type VARCHAR(16) NOT NULL CHECK(career_type IN ('NEW','EXPERIENCED')),
                    target_company VARCHAR(255) NOT NULL CHECK(char_length(trim(target_company))>0),
                    target_role VARCHAR(255) NOT NULL CHECK(char_length(trim(target_role))>0),
                    target_date DATE NOT NULL CHECK(EXTRACT(DAY FROM target_date)=1 AND EXTRACT(YEAR FROM target_date) BETWEEN 1900 AND 2100)
                )
                """);
            if("PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())){
                s.execute("ALTER TABLE student_career_goal ENABLE ROW LEVEL SECURITY");
                s.execute("REVOKE ALL ON TABLE student_career_goal FROM PUBLIC");
                for(String role:List.of("anon","authenticated","service_role"))
                    try(var q=c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")){
                        q.setString(1,role);try(var r=q.executeQuery()){
                            if(r.next())s.execute("REVOKE ALL ON TABLE student_career_goal FROM \""+role+"\"");
                        }
                    }
            }
        }
    }
}
