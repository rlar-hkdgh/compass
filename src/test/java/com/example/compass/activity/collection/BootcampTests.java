package com.example.compass.activity.collection;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BootcampTests {
    private String fixture(String name)throws Exception{try(var s=getClass().getResourceAsStream("/activity/bootcamp/"+name+".html")){return new String(Objects.requireNonNull(s).readAllBytes(),StandardCharsets.UTF_8);}}
    private BootcampParser.Offer codeit(){return new BootcampParser.Offer("CODEIT_SPRINT","529","모집 중 Spring 백엔드 엔지니어 26.11.12 ~ 27.06.11 수강","https://sprint.codeit.kr/admissions/529");}
    private BootcampParser.Offer soldesk(){return new BootcampParser.Offer("SOLDESK","311","AI 클라우드 캠퍼스","https://soldesk.com/shop_view/?idx=311");}
    private String codeitList(int count){StringBuilder s=new StringBuilder();for(int i=1;i<=count;i++)s.append("<a href='/admissions/").append(i).append("'>모집 중 Spring 백엔드 엔지니어</a>");return s.toString();}
    private String soldeskList(){return "<div class='shop-item'><div class='item-pay'><h2>AI 클라우드 캠퍼스</h2></div><div class='item-detail'>교육 주요 정보 총 1050시간</div><a href='/shop_view/?idx=311'>과정 보기</a></div>";}
    @Test void discoversFourTechnicalCodeitAdmissionsAndExcludesStartupDesignAndPending()throws Exception{
        var list=BootcampParser.listing("CODEIT_SPRINT",fixture("codeit-home"));assertThat(list).extracting(BootcampParser.Offer::id).containsExactly("465","529","464","496");
    }
    @Test void discoversSoldeskLongItCoursesAndExcludesShortEmployeeClasses()throws Exception{
        var list=BootcampParser.listing("SOLDESK",fixture("soldesk-home"));assertThat(list).extracting(BootcampParser.Offer::id).containsExactly("311","312","313","315");
    }
    @Test void parsesActualCodeitCurriculumAndAuthoritativeCourseDates()throws Exception{
        var row=BootcampParser.detail(codeit(),fixture("codeit-spring")).orElseThrow();assertThat(row.name()).contains("Spring","백엔드");assertThat(row.startsOn()).isEqualTo(LocalDate.of(2026,11,12));assertThat(row.endsOn()).isEqualTo(LocalDate.of(2027,6,11));
        assertThat(row.applicationStartAt()).isNull();assertThat(row.applicationEndAt()).isEqualTo(OffsetDateTime.parse("2026-11-08T23:59:59+09:00"));
        assertThat(row.description()).contains("Java","Git","60만 원").doesNotContain("로그인 개인정보처리방침");assertThat(row.detail()).isEmpty();
    }
    @Test void parsesSoldeskTemplateAndDistinguishesPreparatoryAndMainTraining()throws Exception{
        var row=BootcampParser.detail(soldesk(),fixture("soldesk-cloud")).orElseThrow();assertThat(row.startsOn()).isEqualTo(LocalDate.of(2026,12,29));assertThat(row.endsOn()).isEqualTo(LocalDate.of(2027,6,29));
        assertThat(row.applicationStartAt()).isNull();assertThat(row.applicationEndAt()).isNull();assertThat(row.description()).contains("MLOps","기초교육","1050");
    }
    @Test void extractsPublishedApplicationRangeWithoutUsingEducationDates()throws Exception{
        String html=fixture("soldesk-cloud").replace("본 교육일정", "모집 기간 2026.11.01 09:00 ~ 2026.12.20 18:00 교육 기간 안내 본 교육일정");
        var row=BootcampParser.detail(soldesk(),html).orElseThrow();assertThat(row.applicationStartAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T09:00:00+09:00"));assertThat(row.applicationEndAt()).isEqualTo(OffsetDateTime.parse("2026-12-20T18:00:00+09:00"));
        assertThat(row.startsOn()).isEqualTo(LocalDate.of(2026,12,29));
    }
    @Test void preservesExplicitDeadlineAndStartOnlyTimes()throws Exception{
        var end=BootcampParser.detail(soldesk(),fixture("soldesk-cloud").replace("본 교육일정","모집 마감: 2026.12.20 18:00 본 교육일정")).orElseThrow();
        assertThat(end.applicationEndAt()).isEqualTo(OffsetDateTime.parse("2026-12-20T18:00:00+09:00"));
        var start=BootcampParser.detail(soldesk(),fixture("soldesk-cloud").replace("본 교육일정","신청 기간 2026.11.01 09:00부터 교육 기간 안내 본 교육일정")).orElseThrow();
        assertThat(start.applicationStartAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T09:00:00+09:00"));assertThat(start.applicationEndAt()).isNull();
    }
    @Test void failsClosedOnLoginMissingCurriculumAndMismatchedCourseOrDates()throws Exception{
        assertThatThrownBy(()->BootcampParser.detail(codeit(),"<form>로그인</form>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BootcampParser.detail(codeit(),fixture("codeit-spring").replace("admission-detail-curriculum","changed-curriculum"))).isInstanceOf(IllegalArgumentException.class);
        var wrong=new BootcampParser.Offer("CODEIT_SPRINT","529","프론트엔드 26.11.12 ~ 27.06.11","https://sprint.codeit.kr/admissions/529");assertThatThrownBy(()->BootcampParser.detail(wrong,fixture("codeit-spring"))).isInstanceOf(IllegalArgumentException.class);
        var stale=new BootcampParser.Offer("CODEIT_SPRINT","529","Spring 백엔드 26.11.13 ~ 27.06.11","https://sprint.codeit.kr/admissions/529");assertThatThrownBy(()->BootcampParser.detail(stale,fixture("codeit-spring"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BootcampParser.detail(soldesk(),fixture("soldesk-cloud").replace("2027.06.29","2025.06.29"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusesForeignHostsInjectedQueryAndWrongProductIdentity()throws Exception{
        for(String url:List.of("http://soldesk.com/","https://soldesk.com/shop_view/?idx=311&extra=1","https://user@soldesk.com/","https://soldesk.com.evil.example/"))assertThatThrownBy(()->BootcampParser.validate("SOLDESK",java.net.URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BootcampParser.detail(soldesk(),fixture("soldesk-cloud").replace("https://soldesk.com/shop_view/?idx=311","https://soldesk.com/shop_view/?idx=312"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workflowFetchesOnlyFilteredCandidatesAndSkipsExpiredCohorts()throws Exception{
        List<String> calls=new ArrayList<>();BootcampCollector.PageReader reader=(source,uri)->{calls.add(uri.toString());if(uri.getPath().equals("/"))return source.equals("CODEIT_SPRINT")?"<a href='/admissions/529'>모집 중 Spring 백엔드 엔지니어 26.11.12 ~ 27.06.11</a><a href='/admissions/466'>프로덕트 디자이너</a>":soldeskList();return fixture(source.equals("CODEIT_SPRINT")?"codeit-spring":"soldesk-cloud");};
        assertThat(BootcampCollector.collect(LocalDate.of(2026,11,2),reader)).hasSize(2);assertThat(calls).hasSize(4).noneMatch(s->s.contains("466"));
        assertThat(BootcampCollector.collect(LocalDate.of(2027,1,1),reader)).isEmpty();
    }
    @Test void acceptsListingsBeyondFormerCapsAndPropagatesFailures() {
        assertThat(BootcampParser.listing("CODEIT_SPRINT",codeitList(501))).hasSize(501);
        java.util.concurrent.atomic.AtomicInteger details=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->BootcampCollector.collect(LocalDate.of(2026,11,2),(source,uri)->{if(uri.getPath().equals("/"))return source.equals("CODEIT_SPRINT")?codeitList(51):soldeskList();details.incrementAndGet();throw new java.io.IOException("detail reached");})).isInstanceOf(java.io.IOException.class).hasMessage("detail reached");
        assertThat(details.get()).isEqualTo(1);
        assertThatThrownBy(()->BootcampCollector.collect(LocalDate.now(),(source,uri)->{throw new java.io.IOException("stopped");})).isInstanceOf(java.io.IOException.class);
    }
    @Test void deduplicatesLinksAndKeepsThreeSecondPacingAndBoundedHtmlSize()throws Exception{
        var list=BootcampParser.listing("CODEIT_SPRINT",codeitList(1)+codeitList(1));assertThat(list).hasSize(1);
        List<String> calls=new ArrayList<>();var client=new OfficialActivityClient(){@Override String request(String url){calls.add(url);return "offline";}};
        assertThat(client.fetchBootcamp("CODEIT_SPRINT",BootcampParser.listUri("CODEIT_SPRINT"))).isEqualTo("offline");assertThat(calls).containsExactly("https://sprint.codeit.kr/");
        assertThat(OfficialActivityClient.requestInterval(java.net.URI.create(soldesk().url()))).isEqualTo(3000);assertThat(OfficialActivityClient.bodyLimit(java.net.URI.create(soldesk().url()))).isEqualTo(4_000_000);
    }
}
