package com.example.compass.activity.collection;

import com.example.compass.activity.entity.CareerActivity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ContextConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class BootcampPersistenceTests {
    @Autowired EntityManager em;
    @Autowired CollectedActivityStore store;
    @Test void sourceSeedsAndActualParsedContentRoundTripIntoExistingGeneralTable()throws Exception{
        for(String source:new String[]{"CODEIT_SPRINT","SOLDESK"}){
            String file=source.equals("CODEIT_SPRINT")?"codeit-spring":"soldesk-cloud",id=source.equals("CODEIT_SPRINT")?"529":"311",label=source.equals("CODEIT_SPRINT")?"Spring 백엔드 엔지니어":"AI 클라우드 캠퍼스",url=source.equals("CODEIT_SPRINT")?"https://sprint.codeit.kr/admissions/529":"https://soldesk.com/shop_view/?idx=311";
            String html;try(var s=getClass().getResourceAsStream("/activity/bootcamp/"+file+".html")){html=new String(Objects.requireNonNull(s).readAllBytes(),StandardCharsets.UTF_8);}
            var value=BootcampParser.detail(new BootcampParser.Offer(source,id,label,url),html).orElseThrow();Long saved=store.save(value);assertThat(store.save(value)).isEqualTo(saved);em.clear();
            var row=em.find(CareerActivity.class,saved);assertThat(row.getSourceCode()).isEqualTo(source);assertThat(row.getActivityTypeCode()).isEqualTo("BOOTCAMP");assertThat(row.getDescription()).contains("교육");assertThat(row.getStartsOn()).isAfter(LocalDate.of(2026,11,1));assertThat(row.getEndsOn()).isNotNull();
        }
    }
}
