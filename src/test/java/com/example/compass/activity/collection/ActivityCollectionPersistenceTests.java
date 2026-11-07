package com.example.compass.activity.collection;

import com.example.compass.activity.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class ActivityCollectionPersistenceTests {
    @Autowired CollectedActivityStore store;
    @Autowired EntityManager em;
    private CollectedActivity activity(String type,String source,String id,Map<String,String> course){
        String host=switch(source){case "SEJONG_COURSE","SEJONG_JOB_CENTER"->"www.sejong.ac.kr";case "SEJONG_DODREAM"->"do.sejong.ac.kr";default->"www.ssafy.com";};
        return new CollectedActivity(type,source,id,"https://"+host+"/fixture","프로그래밍 개발자 교육","공식기관","프로그래밍과 개발 업무를 수행하는 교육입니다.","프로그래밍 개발자",course);
    }
    @Test void storesCourseIndependentlyAndThreeGeneralTypesInOneTable(){
        var course=activity("COURSE","SEJONG_COURSE","2026:SECOND_SEMESTER:3513:001234:001",Map.of("courseNumber","001234","academicYear","2026","semester","SECOND_SEMESTER","sectionNumber","001","departmentCode","3513","credits","3"));
        Long courseId=store.save(course);assertThat(store.save(course)).isEqualTo(courseId);
        for(String type:List.of("EXTRACURRICULAR","INTERNSHIP","BOOTCAMP"))store.save(activity(type,switch(type){case "EXTRACURRICULAR"->"SEJONG_DODREAM";case "INTERNSHIP"->"SEJONG_JOB_CENTER";default->"SSAFY";},type,Map.of()));
        em.clear();assertThat(em.find(CourseActivity.class,courseId).getCourseNumber()).isEqualTo("001234");
        assertThat(em.createQuery("select count(a) from CareerActivity a",Long.class).getSingleResult()).isEqualTo(3);
        assertThat(em.createQuery("select count(c) from CourseActivity c",Long.class).getSingleResult()).isEqualTo(1);
    }
    @Test void updatesSameSourceAndPreservesUnspecifiedDates(){
        var row=new CollectedActivity("BOOTCAMP","SSAFY","same","https://www.ssafy.com/","프로그래밍 교육","SSAFY","소프트웨어 개발 교육입니다.","프로그래밍",Map.of(),LocalDate.of(2027,1,1),LocalDate.of(2027,12,31),null,OffsetDateTime.parse("2026-11-09T23:59:00+09:00"));
        Long id=store.save(row);assertThat(store.save(activity("BOOTCAMP","SSAFY","same",Map.of()))).isEqualTo(id);em.clear();
        assertThat(em.find(CareerActivity.class,id).getStartsOn()).isEqualTo(LocalDate.of(2027,1,1));assertThat(em.find(CareerActivity.class,id).getApplicationEndAt()).isNotNull();
    }
    @Test void savesActualJobAndDodreamTextAndSeparatePeriodFields()throws Exception{
        String job;try(var s=getClass().getResourceAsStream("/activity/job-center/detail-shinhan.html")){job=new String(Objects.requireNonNull(s).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String token=org.jsoup.Jsoup.parse(job).selectFirst("input#rcdx").val();var parsed=SejongJobCenterParser.detail(new SejongJobCenterParser.Posting("22128",token,"인턴"),job).orElseThrow();
        Long id=store.save(parsed);em.clear();assertThat(em.find(CareerActivity.class,id).getDescription()).contains("정보보안","235만원");assertThat(em.find(CareerActivity.class,id).getApplicationEndAt().toInstant()).isEqualTo(OffsetDateTime.parse("2026-11-05T09:00:00+09:00").toInstant());
        String extra;try(var s=getClass().getResourceAsStream("/activity/dodream/detail-code.html")){extra=new String(Objects.requireNonNull(s).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var program=SejongDodreamParser.detail(new SejongDodreamParser.Program("4346","코딩경시대회"),extra).orElseThrow();Long extraId=store.save(program);em.clear();assertThat(em.find(CareerActivity.class,extraId).getStartsOn()).isEqualTo(LocalDate.of(2026,10,26));
    }
    @Test void rejectsCertificationsOldDetailFieldsAndTypeChanges(){
        assertThatThrownBy(()->store.save(activity("CERTIFICATION","QNET","QNET_1320",Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.save(activity("BOOTCAMP","SSAFY","bad",Map.of("supportText","지원")))).isInstanceOf(IllegalArgumentException.class);
        var first=activity("BOOTCAMP","SSAFY","same",Map.of());Long id=store.save(first);em.find(CareerActivity.class,id).setActivityTypeCode("INTERNSHIP");em.flush();
        assertThatThrownBy(()->store.save(first)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
    }
}
