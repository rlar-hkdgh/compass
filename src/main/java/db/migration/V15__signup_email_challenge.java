package db.migration;
import org.flywaydb.core.api.migration.*;
import java.util.List;
/** Additive: preserves all accounts and V1-V14 data. No verification backfill. */
public class V15__signup_email_challenge extends BaseJavaMigration {
 @Override public Integer getChecksum(){return 15001;}
 @Override public void migrate(Context context)throws Exception{
  var c=context.getConnection();
  try(var s=c.createStatement()){
   s.execute("""
    CREATE TABLE signup_email_challenge (
     school_email VARCHAR(254) PRIMARY KEY,
     owner_hash VARCHAR(64),
     code_hash VARCHAR(255),
     expires_at TIMESTAMP WITH TIME ZONE,
     verified_until TIMESTAMP WITH TIME ZONE,
     last_sent_at TIMESTAMP WITH TIME ZONE,
     window_started_at TIMESTAMP WITH TIME ZONE,
     send_count INTEGER NOT NULL DEFAULT 0 CHECK(send_count BETWEEN 0 AND 10),
     failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK(failed_attempts BETWEEN 0 AND 5),
     CHECK((code_hash IS NULL AND expires_at IS NULL) OR (code_hash IS NOT NULL AND expires_at IS NOT NULL)),
     CHECK(verified_until IS NULL OR code_hash IS NULL)
    )
    """);
   if("PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())){
    s.execute("ALTER TABLE signup_email_challenge ENABLE ROW LEVEL SECURITY");
    s.execute("REVOKE ALL ON TABLE signup_email_challenge FROM PUBLIC");
    for(String role:List.of("anon","authenticated","service_role"))
     try(var q=c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")){
      q.setString(1,role);try(var r=q.executeQuery()){if(r.next())s.execute("REVOKE ALL ON TABLE signup_email_challenge FROM \""+role+"\"");}
     }
   }
  }
 }
}
