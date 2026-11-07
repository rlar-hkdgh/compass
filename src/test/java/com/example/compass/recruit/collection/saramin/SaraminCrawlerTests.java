package com.example.compass.recruit.collection.saramin;


import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class SaraminCrawlerTests {
    static final String SUMMARY=SaraminTestFixtures.SUMMARY;
    static final String BODY="<div class='user_content'><h2>지원자격</h2><p>Java와 SQL 개발 경험을 요구합니다. 역할과 수행 내용을 구체적으로 검토합니다.</p>"
        +"<h2>우대사항</h2><p>PostgreSQL 사용 경험과 팀 코드 리뷰 경험을 우대합니다. 실제 테스트를 위한 가상 설명입니다.</p>"
        +"<script>SHOULD_NOT_BE_SAVED</script></div>";
    @Test void onlyCurrentPostingTagsAndBodyAreCollected() {
        var data=SaraminPageParser.parse("123",SUMMARY,BODY,"https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=123");
        assertThat(data.codes().get("cat_kewd")).containsExactly("84");
        assertThat(data.codes().get("loc_cd")).containsExactly("101070","101050");
        assertThat(data.description()).contains("지원자격","우대사항").doesNotContain("SHOULD_NOT_BE_SAVED");
        assertThat(data.applicationStart()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
        assertThat(data.active()).isTrue();
    }
    @Test void wrongPostingOrImageOnlyBodyIsNotSavedAsComplete() {
        assertThatThrownBy(()->SaraminPageParser.parse("456",SUMMARY,BODY,"unused")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SaraminPageParser.parse("123",SUMMARY,"<img src='x.png'>","unused")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void robotsRulesAreCheckedForOurAgent() {
        String rules="User-agent: GPTBot\nDisallow: /\nUser-agent: *\nDisallow: /private/\nDisallow: /blocked*\nAllow: /private/public\n";
        assertThat(SaraminCrawler.allowed(rules,"/zf_user/jobs/relay/view")).isTrue();
        assertThat(SaraminCrawler.allowed(rules,"/private/a")).isFalse();
        assertThat(SaraminCrawler.allowed(rules,"/blocked?x=1")).isFalse();
        assertThat(SaraminCrawler.allowed(rules,"/private/public")).isTrue();
    }
    @Test void deadlinePolicyPrefersExplicitRecruitmentTextOverADate() {
        Instant end=Instant.parse("2099-11-30T14:59:00Z");
        assertThat(SaraminPageParser.deadline("2099.11.30 23:59","일반 공고",end).code()).isEqualTo("1");
        assertThat(SaraminPageParser.deadline("채용시 마감","일반 공고",null).code()).isEqualTo("2");
        assertThat(SaraminPageParser.deadline("상시 모집","일반 공고",null).code()).isEqualTo("3");
        assertThat(SaraminPageParser.deadline("2099.11.30 23:59","해당 공고는 수시 채용으로 조기에 마감될 수 있습니다.",end).code()).isEqualTo("4");
        assertThat(SaraminPageParser.deadline("2099.11.30 23:59","채용 완료 시 조기에 마감될 수 있습니다.",end).code()).isEqualTo("1");
    }
    @Test void unknownOrConflictingDeadlineDoesNotInventACode() {
        assertThat(SaraminPageParser.deadline("","일반 설명",null).code()).isNull();
        assertThat(SaraminPageParser.deadline("","상시 채용 또는 수시 모집",null).code()).isNull();
    }
    @Test void unknownKeywordCodeAndDisplayedNameArePreservedWithoutGuessingClassification() {
        String html=SUMMARY.replace("<div class=\"tags\">",
            "<div class=\"tags\"><a href='/zf_user/jobs/list/job-category?cat_kewd=503864'>#RTL</a>"
            +"<a href='/zf_user/jobs/list/job-category?cat_kewd=147191'>#SoC</a>");
        var page=org.jsoup.Jsoup.parse(html,"https://www.saramin.co.kr");
        assertThat(SaraminPageParser.originalCodes(page).get("cat_kewd")).contains("503864","147191","84");
        assertThat(SaraminPageParser.keywordNames(page)).containsEntry("503864","RTL").containsEntry("147191","SoC");
    }
    @Test void missingOrNonHttpCompanyLinkIsOptionalAndValidRelativeLinkIsKept() {
        String original="href=\"/zf_user/company-info/view?csn=example\"";
        for(String replacement:java.util.List.of("","href=\"javascript:void(0)\"","href=\"#\"")) {
            var data=SaraminPageParser.parse("123",SUMMARY.replace(original,replacement),BODY,"unused");
            assertThat(data.companyUrl()).isNull();
        }
        assertThat(SaraminPageParser.parse("123",SUMMARY,BODY,"unused").companyUrl())
            .isEqualTo("https://www.saramin.co.kr/zf_user/company-info/view?csn=example");
    }
}
