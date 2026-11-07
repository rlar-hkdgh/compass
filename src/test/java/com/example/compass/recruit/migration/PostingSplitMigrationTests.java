package com.example.compass.recruit.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PostingSplitMigrationTests {
    @Test void upgradesV3WithoutChangingValuesLinksOrMetadataAndNarrowsEveryReference() throws Exception {
        String url="jdbc:h2:mem:split-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("3").load().migrate();
        String id="p".repeat(64),body="# 공고\n".repeat(1000),metadata="{\"legacySalaryType\":\"11\",\"bodyOcr\":[],\"summary\":{\"학력\":\"원문\"}}";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO company(id,name) VALUES(1,'keep')");
            try(var p=c.prepareStatement("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,source_url,active,keywords,description,source_metadata) VALUES('SARAMIN',?,1,'keep','https://example.com',true,'Java',?,?)")) {p.setString(1,id);p.setString(2,body);p.setString(3,metadata);p.executeUpdate();}
            s.executeUpdate("INSERT INTO posting_job_category VALUES('SARAMIN','"+id+"','84')");
            s.executeUpdate("INSERT INTO posting_region SELECT 'SARAMIN','"+id+"',min(code) FROM region");
            s.executeUpdate("INSERT INTO posting_employment_type SELECT 'SARAMIN','"+id+"',min(code) FROM employment_type");
            s.executeUpdate("INSERT INTO posting_specialty SELECT 'SARAMIN','"+id+"',min(code) FROM specialty");
            s.executeUpdate("INSERT INTO posting_tech_stack SELECT 'SARAMIN','"+id+"',min(code) FROM tech_stack");
            s.executeUpdate("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,source_url,active) VALUES('SARAMIN','empty',1,'empty','https://example.com',true)");
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").target("4").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var row=s.executeQuery("SELECT d.source_url,d.keywords,d.description,m.source_metadata FROM job_posting p JOIN job_posting_detail d USING(source_code,source_posting_id) JOIN job_posting_collection_metadata m USING(source_code,source_posting_id) WHERE p.source_posting_id='"+id+"'")) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("https://example.com");assertThat(row.getString(2)).isEqualTo("Java");assertThat(row.getString(3)).isEqualTo(body);assertThat(row.getString(4)).isEqualTo(metadata);}
            try(var row=s.executeQuery("SELECT d.description,m.source_metadata FROM job_posting_detail d JOIN job_posting_collection_metadata m USING(source_code,source_posting_id) WHERE d.source_posting_id='empty'")) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isNull();assertThat(row.getString(2)).isNull();}
            for(String table:new String[]{"posting_job_category","posting_region","posting_employment_type","posting_specialty","posting_tech_stack"}) {
                try(var row=s.executeQuery("SELECT count(*) FROM "+table)) {row.next();assertThat(row.getInt(1)).as(table).isEqualTo(1);}
            }
            try(var row=s.executeQuery("SELECT count(*),min(character_maximum_length),max(character_maximum_length) FROM information_schema.columns WHERE table_schema='PUBLIC' AND column_name='SOURCE_POSTING_ID'")) {row.next();assertThat(row.getInt(1)).isEqualTo(8);assertThat(row.getInt(2)).isEqualTo(64);assertThat(row.getInt(3)).isEqualTo(64);}
            assertThatThrownBy(()->s.executeQuery("SELECT description FROM job_posting")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO job_posting_detail VALUES('SARAMIN','missing','https://example.com',NULL,NULL)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,active) VALUES('SARAMIN','"+"x".repeat(65)+"',1,'too long',true)")).isInstanceOf(SQLException.class);
        }
    }
}
