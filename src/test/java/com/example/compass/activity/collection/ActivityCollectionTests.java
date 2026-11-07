package com.example.compass.activity.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ActivityCollectionTests {
    @TempDir Path temp;
    private ActivityCollectionPlan.Target target(String type,String source,String url,Map<String,String> detail) {
        return new ActivityCollectionPlan.Target(type,source,url,"article.activity","h1",".evidence","공식기관",detail);
    }
    private String page(String title,String evidence) { return "<article class='activity'><h1>"+title+"</h1><div class='evidence'>"+evidence+"</div></article>"; }
    @Test void filtersNonItAndDoesNotMatchEmbeddedEnglishLetters() {
        assertThat(ActivityItPolicy.accepts("COURSE","알고리즘","자료구조 학습")).isTrue();
        assertThat(ActivityItPolicy.accepts("BOOTCAMP","JAVA 개발 과정","백엔드 개발 교육")).isTrue();
        assertThat(ActivityItPolicy.accepts("COURSE","국제정치","leadership training")).isFalse();
        assertThat(ActivityItPolicy.accepts("INTERNSHIP","소프트웨어 기업 마케팅 인턴","영업 및 홍보 담당")).isFalse();
        assertThat(ActivityItPolicy.accepts("INTERNSHIP","백엔드 개발자 인턴","대학일자리플러스센터 개발 업무")).isTrue();
    }
    @Test void rejectsCertificationWebTargetsAndKeepsGenericDatesUnknown() {
        assertThatThrownBy(()->OfficialActivityParser.validateTarget(target("CERTIFICATION","KAIT","https://www.ihd.or.kr/introducesubject1.do",Map.of()))).isInstanceOf(IllegalArgumentException.class);
        var row=OfficialActivityParser.parse(target("BOOTCAMP","SSAFY","https://www.ssafy.com/",Map.of()),page("프로그래밍 교육","소프트웨어 개발 교육을 제공하는 공식 프로그램입니다.")).orElseThrow();
        assertThat(row.startsOn()).isNull();assertThat(row.applicationEndAt()).isNull();assertThat(row.detail()).isEmpty();
    }
    @Test void rejectsUntrustedHostsSchemesAndSourceTypeMismatch() {
        for(String url:List.of("http://www.ssafy.com/","https://evil.example/","https://www.ssafy.com.evil.example/","https://user@www.ssafy.com/","https://www.ssafy.com:9999/"))
            assertThatThrownBy(()->OfficialActivityParser.validateTarget(target("BOOTCAMP","SSAFY",url,Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->OfficialActivityParser.validateTarget(target("COURSE","SSAFY","https://www.ssafy.com/",Map.of()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failsClosedOnMissingAmbiguousOrWholePageSelectors() {
        var t=target("BOOTCAMP","SSAFY","https://www.ssafy.com/",Map.of());
        assertThatThrownBy(()->OfficialActivityParser.parse(t,"<body>Login</body>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->OfficialActivityParser.parse(t,page("소프트웨어 과정","프로그래밍 교육을 진행하는 공식 프로그램입니다.")+page("다른 과정","웹 개발"))).isInstanceOf(IllegalArgumentException.class);
        var broad=new ActivityCollectionPlan.Target(t.type(),t.source(),t.url(),"body","h1","p",t.provider(),Map.of());
        assertThatThrownBy(()->OfficialActivityParser.parse(broad,page("개발 교육","프로그래밍 교육"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void requiresCareerCenterProvenanceAndDistinctRepostedRoles() {
        var t=target("INTERNSHIP","SEJONG_JOB_CENTER","https://dept.sejong.ac.kr/x/board.do?mode=view&articleNo=123",Map.of("companyName","기업A"));
        assertThatThrownBy(()->OfficialActivityParser.parse(t,page("개발자 인턴","기업A의 백엔드 개발 업무를 수행합니다."))).isInstanceOf(IllegalArgumentException.class);
        var a=OfficialActivityParser.parse(t,page("개발자 인턴","대학일자리플러스센터 기업A 백엔드 개발 업무를 수행합니다.")).orElseThrow();
        var b=OfficialActivityParser.parse(t,page("데이터 엔지니어 인턴","대학일자리플러스센터 기업A 데이터 엔지니어 업무입니다.")).orElseThrow();
        assertThat(a.externalId()).isNotEqualTo(b.externalId());
    }
    @Test void verifiesProgramIdentityAndRejectsRetiredDetailFields() {
        var t=target("EXTRACURRICULAR","SEJONG_DODREAM","https://do.sejong.ac.kr/ko/program/all/view/4391",Map.of("programId","4391"));
        var row=OfficialActivityParser.parse(t,page("프로그래밍 특강","학부생 대상 소프트웨어 개발 교육입니다.")).orElseThrow();assertThat(row.externalId()).isEqualTo("4391");assertThat(row.detail()).isEmpty();
        var old=target(t.type(),t.source(),t.url(),Map.of("programId","4391","capacity","30"));
        assertThatThrownBy(()->OfficialActivityParser.parse(old,page("프로그래밍 특강","학부생 30명 대상 소프트웨어 개발 교육입니다."))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsExtraTargetsRejectsDuplicateIdentitiesAndWrongCourseAdapter() {
        var t=target("BOOTCAMP","SSAFY","https://www.ssafy.com/",Map.of());
        ActivityCollectionRunner.validatePlan(new ActivityCollectionPlan(Collections.nCopies(71,t)));
        var a=OfficialActivityParser.parse(t,page("프로그래밍 교육","소프트웨어 개발자를 위한 공식 교육 프로그램입니다.")).orElseThrow();
        assertThatThrownBy(()->ActivityCollectionRunner.validateCandidates(List.of(a,a))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ActivityCollectionRunner.validatePlan(new ActivityCollectionPlan(List.of(target("COURSE","SEJONG_COURSE","https://www.sejong.ac.kr/",Map.of()))))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void readsKoreanTimetablePreservesZerosFiltersItAndBlocksXmlEntities() throws Exception {
        Path file=workbook("<worksheet><sheetData><row r='1'>"+cell("A1","학수번호")+cell("B1","분반")+cell("C1","교과목명")+cell("D1","개설학과코드")+cell("E1","개설학과전공")+cell("F1","학점")+"</row><row r='2'>"+cell("A2","001234")+cell("B2","001")+cell("C2","자료구조")+cell("D2","3513")+cell("E2","컴퓨터공학과")+cell("F2","3")+"</row><row r='3'>"+cell("A3","001235")+cell("B3","001")+cell("C3","한국사")+"</row></sheetData></worksheet>");
        var result=SejongCourseWorkbook.read(file,2026,"SECOND_SEMESTER","https://www.sejong.ac.kr/kor/intro/notice3.do?articleNo=891485&mode=view");
        assertThat(result).hasSize(1);assertThat(result.get(0).detail()).containsEntry("courseNumber","001234").containsEntry("sectionNumber","001");
        assertThatThrownBy(()->SejongCourseWorkbook.read(file,2027,"FIRST_SEMESTER","https://www.sejong.ac.kr/")).isInstanceOf(IllegalArgumentException.class);
        Path malicious=workbook("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///never-read'>]><worksheet><sheetData>&e;</sheetData></worksheet>");
        assertThatThrownBy(()->SejongCourseWorkbook.read(malicious,2026,"SECOND_SEMESTER","https://www.sejong.ac.kr/")).isInstanceOf(Exception.class);
    }
    @Test void previewSerializesConfirmedDatesWithoutRemovedRawMetadata() {
        var row=new CollectedActivity("BOOTCAMP","SSAFY","same","https://www.ssafy.com/","프로그래밍 교육","기관","소프트웨어 개발 교육","프로그래밍",Map.of(),java.time.LocalDate.of(2027,1,1),java.time.LocalDate.of(2027,6,1),null,java.time.OffsetDateTime.parse("2026-11-09T23:59:00+09:00"));
        String json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(row);
        assertThat(json).contains("2027-01-01","2026-11-09").doesNotContain("rawContent","sourceMetadata","checkedAt");
    }
    @Test void acceptsCandidatesAndPlanBeyondFormerCountCaps() {
        var rows=java.util.stream.IntStream.range(0,501).mapToObj(i->new CollectedActivity("BOOTCAMP","SSAFY",Integer.toString(i),"https://www.ssafy.com/","프로그래밍 교육","기관","소프트웨어 개발 교육","프로그래밍",Map.<String,String>of())).toList();
        ActivityCollectionRunner.validateCandidates(rows);
        var targets=java.util.stream.IntStream.range(0,71).mapToObj(i->target("BOOTCAMP","SSAFY","https://www.ssafy.com/",Map.<String,String>of())).toList();
        ActivityCollectionRunner.validatePlan(new ActivityCollectionPlan(targets));
        assertThatThrownBy(()->ActivityCollectionRunner.validateCandidates(java.util.List.of(rows.get(0),rows.get(0)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }
    private static String cell(String id,String value) { return "<c r='"+id+"' t='inlineStr'><is><t>"+value+"</t></is></c>"; }
    private Path workbook(String xml) throws Exception {
        Path file=temp.resolve(UUID.randomUUID()+".xlsx");
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return file;
    }
}
