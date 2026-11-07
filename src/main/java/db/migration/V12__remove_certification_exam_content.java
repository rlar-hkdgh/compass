package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.SQLException;

/** Remove unused content only when no stored value would be lost. */
public class V12__remove_certification_exam_content extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 12001; }
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM certification WHERE exam_content IS NOT NULL")) {
            rows.next();
            if (rows.getLong(1) != 0) throw new SQLException("certification.exam_content has stored values; review before removal");
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE certification DROP COLUMN exam_content");
        }
    }
}
