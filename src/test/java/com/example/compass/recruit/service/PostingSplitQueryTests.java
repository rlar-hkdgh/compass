package com.example.compass.recruit.service;

import com.example.compass.recruit.dto.request.*;
import com.example.compass.recruit.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:split-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.example.compass.recruit.service.PostingSplitQueryTests$SqlCapture"})
@Transactional
public class PostingSplitQueryTests {
    public static class SqlCapture implements StatementInspector {
        static final List<String> sql=new ArrayList<>();
        @Override public String inspect(String value) { sql.add(value.toLowerCase(Locale.ROOT));return value; }
    }
    @Autowired RecruitService service;
    @Autowired EntityManager em;
    @Test void listNeverReadsDetailOrMetadataAndDetailPreservesResponseAndUpdateBehavior() {
        long company=service.createCompany(new CompanyRequest("split test",null)).id();
        var codes=new CodeSelection(List.of(),List.of(),List.of(),null,null,null,null,null,List.of(),List.of());
        var request=new PostingRequest(company,"title","https://example.com",true,null,null,"Java","body",null,null,null,codes);
        String id="q".repeat(64);
        service.save("SARAMIN",id,request);
        var metadata=new JobPostingCollectionMetadata(new PostingId("SARAMIN",id));metadata.setSourceMetadata("{\"keep\":true}");em.persist(metadata);em.flush();em.clear();
        SqlCapture.sql.clear();
        assertThat(service.list(0,10,"SARAMIN",null,null,null).content()).hasSize(1);
        assertThat(SqlCapture.sql).noneMatch(sql->sql.contains("job_posting_detail")||sql.contains("job_posting_collection_metadata")||sql.contains("source_metadata")||sql.contains("description"));
        em.clear();SqlCapture.sql.clear();
        var detail=service.get("SARAMIN",id);assertThat(detail.description()).isEqualTo("body");assertThat(detail.sourceUrl()).isEqualTo("https://example.com");assertThat(detail.keywords()).isEqualTo("Java");
        assertThat(SqlCapture.sql).anyMatch(sql->sql.contains("job_posting_detail")).noneMatch(sql->sql.contains("job_posting_collection_metadata"));
        service.save("SARAMIN",id,new PostingRequest(company,"updated","https://example.com/new",true,null,null,null,null,null,null,null,codes));em.flush();em.clear();
        assertThat(service.get("SARAMIN",id).description()).isNull();assertThat(service.get("SARAMIN",id).posting().title()).isEqualTo("updated");
        assertThat(em.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN",id)).getSourceMetadata()).isEqualTo("{\"keep\":true}");
        assertThatThrownBy(()->service.get("SARAMIN","x".repeat(65))).isInstanceOf(com.example.compass.recruit.exception.RecruitException.class);
    }
}
