package db.migration;

import org.flywaydb.core.api.migration.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No automatic execution outside the explicitly invoked Flyway/test lifecycle. */
public class V10__simplify_activities_and_current_tasks extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 10001;}
    @Override public void migrate(Context context)throws Exception{
        Connection c=context.getConnection();
        requireZero(c,"SELECT count(*) FROM (SELECT student_id FROM student_active_roadmap GROUP BY student_id HAVING count(*)>1) ambiguous","Multiple current targets for a student; select a current target before V10");
        requireZero(c,"SELECT count(*) FROM career_activity a LEFT JOIN course_activity d ON d.activity_id=a.id WHERE a.activity_type_code='COURSE' AND d.activity_id IS NULL","A course lacks its source detail");
        requireZero(c,"SELECT count(*) FROM career_activity a LEFT JOIN certification_activity d ON d.activity_id=a.id WHERE a.activity_type_code='CERTIFICATION' AND d.activity_id IS NULL","A certification lacks its code mapping");
        for(var entry:Map.of("course_activity","COURSE","certification_activity","CERTIFICATION","extracurricular_activity","EXTRACURRICULAR","internship_activity","INTERNSHIP","bootcamp_activity","BOOTCAMP").entrySet())
            requireZero(c,"SELECT count(*) FROM "+entry.getKey()+" d JOIN career_activity a ON a.id=d.activity_id WHERE a.activity_type_code<>'"+entry.getValue()+"'","Mismatched activity detail");
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/db/changes/activity-final-v10.sql"))){
            for(String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())execute(c,sql);
        }
        execute(c,"INSERT INTO course_activity_v10(id,course_number,academic_year,semester,section_number,name,department_code,department_name,credits,description,source_url) SELECT a.id,d.course_number,d.academic_year,d.semester,d.section_number,a.name,d.department_code,d.department_name,d.credits,a.description,a.source_url FROM course_activity d JOIN career_activity a ON a.id=d.activity_id");
        execute(c,"INSERT INTO career_activity_v10 SELECT id,activity_type_code,source_code,source_activity_id,name,provider_name,description,source_url,application_start_at,application_end_at,starts_on,ends_on FROM career_activity WHERE activity_type_code IN ('EXTRACURRICULAR','INTERNSHIP','BOOTCAMP')");
        // Preserve useful former detail text in description instead of keeping many columns.
        for(String table:List.of("extracurricular_activity","internship_activity","bootcamp_activity"))appendDetails(c,table);
        try(var rows=c.createStatement().executeQuery("SELECT d.certification_code,a.description,d.exam_content FROM certification_activity d JOIN career_activity a ON a.id=d.activity_id ORDER BY a.id")){
            while(rows.next())try(var ps=c.prepareStatement("UPDATE certification SET exam_content=COALESCE(exam_content,'') || ? WHERE code=?")){
                ps.setString(1,"\n"+rows.getString(2)+(rows.getString(3)==null?"":"\n"+rows.getString(3)));ps.setString(2,rows.getString(1));ps.executeUpdate();
            }
        }
        execute(c,"INSERT INTO recommended_task(id,student_id,gap_analysis_id,course_activity_id,career_activity_id,certification_code,title,description,academic_year,period_type,display_order,status,completed_at) SELECT t.id,r.student_id,r.gap_analysis_id,CASE WHEN t.activity_type='COURSE' THEN t.activity_id ELSE NULL END,CASE WHEN t.activity_type IN ('EXTRACURRICULAR','INTERNSHIP','BOOTCAMP') THEN t.activity_id ELSE NULL END,d.certification_code,t.title,t.description,p.academic_year,p.period_type,CAST(ROW_NUMBER() OVER(PARTITION BY r.student_id,p.academic_year,p.period_type ORDER BY p.display_order,t.display_order,t.id) AS INTEGER),t.status,t.completed_at FROM roadmap_task t JOIN roadmap_period p ON p.id=t.roadmap_period_id JOIN career_roadmap r ON r.id=p.career_roadmap_id JOIN student_active_roadmap active ON active.career_roadmap_id=r.id LEFT JOIN certification_activity d ON d.activity_id=t.activity_id AND t.activity_type='CERTIFICATION'");
        // Requested policy: only current tasks survive, no old roadmap/history/snapshot store.
        for(String table:List.of("roadmap_task","student_active_roadmap","roadmap_period","career_roadmap","course_activity","certification_activity","extracurricular_activity","internship_activity","bootcamp_activity","career_activity"))execute(c,"DROP TABLE "+table);
        execute(c,"ALTER TABLE course_activity_v10 RENAME TO course_activity");execute(c,"ALTER TABLE career_activity_v10 RENAME TO career_activity");
        execute(c,"ALTER TABLE activity_source DROP COLUMN is_active");
        for(String table:List.of("course_activity","career_activity","recommended_task","certification_exam_schedule")){
            long next;try(var rs=c.createStatement().executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM "+table)){rs.next();next=rs.getLong(1);}execute(c,"ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH "+next);
        }
        execute(c,"CREATE INDEX idx_recommended_task_gap ON recommended_task(gap_analysis_id)");
    }
    private static void appendDetails(Connection c,String table)throws SQLException{
        try(var rows=c.createStatement().executeQuery("SELECT * FROM "+table)){
            var md=rows.getMetaData();while(rows.next()){
                StringBuilder text=new StringBuilder();for(int i=1;i<=md.getColumnCount();i++)if(!md.getColumnName(i).equalsIgnoreCase("activity_id")){String value=rows.getString(i);if(value!=null&&!value.isBlank())text.append("\n").append(md.getColumnName(i).toLowerCase(Locale.ROOT)).append(": ").append(value);}
                if(!text.isEmpty())try(var ps=c.prepareStatement("UPDATE career_activity_v10 SET description=description || ? WHERE id=?")){ps.setString(1,text.toString());ps.setLong(2,rows.getLong("activity_id"));ps.executeUpdate();}
            }
        }
    }
    private static void execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    private static void requireZero(Connection c,String sql,String error)throws SQLException{try(var s=c.createStatement();var rs=s.executeQuery(sql)){rs.next();if(rs.getLong(1)!=0)throw new SQLException(error);}}
}
