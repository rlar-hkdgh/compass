package com.example.compass.recruit.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BaselineSchemaTests {
    @Test void creates37BusinessTablesThenSeedsAndValidatesWithoutReapplying() throws Exception {
        String url="jdbc:h2:mem:baseline-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var schema=Flyway.configure().dataSource(url,"sa","").target("1").load();
        assertThat(schema.migrate().migrationsExecuted).isEqualTo(1);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var result=s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='PUBLIC' AND table_name<>'flyway_schema_history'")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(37);
            }
            try(var result=s.executeQuery("SELECT count(*) FROM job_category")) {result.next();assertThat(result.getInt(1)).isZero();}
        }
        var all=Flyway.configure().dataSource(url,"sa","").target("2").load();
        assertThat(all.migrate().migrationsExecuted).isEqualTo(1);
        all.validate();
        assertThat(all.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            String[] tables={"posting_source","job_category","specialty","tech_stack","region","employment_type","education_level","experience_type","deadline_type","salary_unit","salary_decision","major_completion_type"};
            int[] counts={2,28,92,140,677,22,10,4,4,6,3,6};
            for(int i=0;i<tables.length;i++) {
                try(var result=s.executeQuery("SELECT count(*) FROM "+tables[i])) {result.next();assertThat(result.getInt(1)).as(tables[i]).isEqualTo(counts[i]);}
            }
            for(String table:new String[]{"company","job_posting","student","major","certification"}) {
                try(var result=s.executeQuery("SELECT count(*) FROM "+table)) {result.next();assertThat(result.getInt(1)).as(table).isZero();}
            }
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO posting_job_category VALUES('SARAMIN','missing','84')")).isInstanceOf(SQLException.class);
            s.executeUpdate("INSERT INTO company(id,name) VALUES(1,'test')");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO job_posting(source_code,source_posting_id,company_id,title,source_url,active,salary_min_amount) VALUES('SARAMIN','invalid',1,'test','https://example.com',true,1)")).isInstanceOf(SQLException.class);
        }
    }
}
