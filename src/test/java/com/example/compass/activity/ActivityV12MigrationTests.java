package com.example.compass.activity;
import org.flywaydb.core.Flyway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class ActivityV12MigrationTests {
    static EmbeddedPostgres pg;
    @BeforeAll static void start() throws Exception { pg=EmbeddedPostgres.builder().setPort(0).start(); }
    @AfterAll static void stop() throws Exception { if(pg!=null)pg.close(); }
    private String v11() throws Exception {
        String db="v12_"+UUID.randomUUID().toString().replace("-","");
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
        String url=pg.getJdbcUrl("postgres",db);
        Flyway.configure().dataSource(url,"postgres","").target("11").load().migrate();return url;
    }
    @Test void removesEmptyColumnAndPreservesCertificationAndSchedule() throws Exception {
        String url=v11();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            s.execute("INSERT INTO certification_exam_schedule(certification_code,exam_year,round_number,exam_stage,starts_on,ends_on) VALUES('KDATA_SQLD',2026,63,'SINGLE',DATE '2026-11-14',DATE '2026-11-14')");
        }
        var flyway=Flyway.configure().dataSource(url,"postgres","").target("12").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name='certification' AND column_name='exam_content'")){r.next();assertThat(r.getInt(1)).isZero();}
            try(var r=s.executeQuery("SELECT count(*) FROM certification")){r.next();assertThat(r.getInt(1)).isEqualTo(9);}
            try(var r=s.executeQuery("SELECT count(*) FROM certification_exam_schedule")){r.next();assertThat(r.getInt(1)).isEqualTo(1);}
        }
    }
    @Test void refusesRemovalWhenContentExists() throws Exception {
        String url=v11();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){s.execute("UPDATE certification SET exam_content='preserve me' WHERE code='KDATA_SQLD'");}
        assertThatThrownBy(()->Flyway.configure().dataSource(url,"postgres","").target("12").load().migrate()).isInstanceOf(Exception.class).hasStackTraceContaining("review before removal");
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT exam_content FROM certification WHERE code='KDATA_SQLD'")){r.next();assertThat(r.getString(1)).isEqualTo("preserve me");}
    }
}
