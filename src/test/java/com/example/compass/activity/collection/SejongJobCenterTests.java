package com.example.compass.activity.collection;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SejongJobCenterTests {
    private static final String TOKEN = "A".repeat(64);
    private static final SejongJobCenterParser.Posting POST = new SejongJobCenterParser.Posting("22145", TOKEN, "개발 인턴");
    private String fixture(String file) throws Exception {
        try (var stream = getClass().getResourceAsStream("/activity/job-center/" + file)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private String listing(String id, String title, int next) {
        return "<table><tr id='hcTr1'><td><a href=\"javascript:detailView('" + TOKEN + "', '" + id + "');\">"
            + title + "</a></td></tr></table><a onclick=\"hcSubmit('/Recruit/RecruitSchoolList.aspx?link=i&rp="
            + next + "', 'Widget1_List','form1')\">다음</a>";
    }
    private String detail(String role, String employment) {
        return "<input id='rcdx' value='" + TOKEN + "'><span id='Title'>기업 인턴</span>"
            + "<span id='bizname'>소프트웨어 기업</span><span id='_job'>" + role + "</span>"
            + "<span id='worktype3'>" + employment + "</span><span id='jobdetail1'>"
            + "소프트웨어와 클라우드를 제공하는 기업입니다. 지원 절차 및 일정은 아래를 확인하세요.</span>"
            + "<span id='edate'>2026-11-16 오전 9:00:00</span>";
    }
    @Test void acceptsObservedEightyCharacterTokensButRejectsUnexpectedLengths() {
        String token = "A".repeat(80);
        var rows = SejongJobCenterParser.listing(listing("22145", "개발 인턴", 1).replace(TOKEN, token), 1);
        assertThat(rows.postings()).hasSize(1);
        var posting = rows.postings().get(0);
        SejongJobCenterParser.validateUri(posting.uri());
        assertThat(SejongJobCenterParser.detail(posting, detail("웹서비스 개발자", "인턴").replace(TOKEN, token))).isPresent();
        for (int length : List.of(63, 65, 79, 81, 128))
            assertThatThrownBy(() -> new SejongJobCenterParser.Posting("1", "A".repeat(length), "인턴"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void readsRealRecommendationRowsAndPagination() throws Exception {
        var first = SejongJobCenterParser.listing(fixture("list.html"), 1);
        assertThat(first.postings()).anyMatch(p -> p.id().equals("22145"))
            .anyMatch(p -> p.id().equals("22128"));
        assertThat(first.postings()).allMatch(p -> p.title().contains("인턴"));
        assertThat(first.lastLinkedPage()).isGreaterThan(1);
        var second = SejongJobCenterParser.listing(fixture("list-page2.html"), 2);
        assertThat(second.postings()).extracting(SejongJobCenterParser.Posting::id)
            .doesNotContain("22145", "22128");
    }
    @Test void readsBothRealItDetailsAndPreservesDeadlineAndConditions() throws Exception {
        for (String file : List.of("detail-star.html", "detail-shinhan.html")) {
            String html = fixture(file);
            String token = Jsoup.parse(html).selectFirst("input#rcdx").val();
            var p = new SejongJobCenterParser.Posting("22145", token, "인턴");
            var value = SejongJobCenterParser.detail(p, html).orElseThrow();
            assertThat(value.detail()).isEmpty();
            assertThat(value.description()).contains("지원", "접수마감일", "2026-11-");
            assertThat(value.itEvidence()).isIn("웹서비스 개발자", "정보보안(채용연계형 인턴)");
            assertThat(value.description()).doesNotContain("__VIEWSTATE", "채용공고를 공유하세요");
        }
    }
    @Test void excludesMarketingEvenWhenEmployerAndBodyMentionSoftware() {
        assertThat(SejongJobCenterParser.detail(POST, detail("마케팅 및 홍보", "인턴"))).isEmpty();
        assertThat(SejongJobCenterParser.detail(POST, detail("정보보안", "신입"))).isEmpty();
        assertThat(SejongJobCenterParser.detail(POST, detail("웹서비스 개발자", "인턴"))).isPresent();
    }
    @Test void failsClosedOnLoginMissingFieldsAndWrongIdentity() throws Exception {
        assertThatThrownBy(() -> SejongJobCenterParser.listing(fixture("alba-list.html"), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SejongJobCenterParser.detail(POST, "<form>로그인</form>"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SejongJobCenterParser.detail(POST, detail("정보보안", "인턴").replace(TOKEN, "B".repeat(64))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SejongJobCenterParser.detail(POST, detail("정보보안", "인턴") + "<span id='_job'>개발자</span>"))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void limitsNetworkScopeAndRejectsInjectedLinks() {
        for (String url : List.of("http://udream.sejong.ac.kr/Recruit/RecruitSchoolList.aspx?link=i&rp=1",
            "https://udream.sejong.ac.kr/Recruit/AlbaList.aspx?link=i",
            "https://evil.example/Recruit/RecruitSchoolList.aspx?link=i&rp=1",
            "https://udream.sejong.ac.kr/Recruit/RecruitSchoolList.aspx?link=i&rp=1&extra=1"))
            assertThatThrownBy(() -> SejongJobCenterParser.validateUri(java.net.URI.create(url)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SejongJobCenterParser.listing(listing("1", "인턴", 1).replace("detailView('", "evil('"), 1))
            .isInstanceOf(IllegalArgumentException.class);
        SejongJobCenterParser.validateUri(POST.uri());
    }
    @Test void stableIdentityIgnoresEncryptedUrlVariantsAndTitleEdits() {
        var a = SejongJobCenterParser.detail(POST, detail("정보보안", "인턴")).orElseThrow();
        var other = new SejongJobCenterParser.Posting(POST.id(), "B".repeat(64), "변경 제목");
        var b = SejongJobCenterParser.detail(other, detail("정보보안", "인턴").replace(TOKEN, other.token())).orElseThrow();
        assertThat(a.externalId()).isEqualTo("udream:22145").isEqualTo(b.externalId());
    }
    @Test void workflowOnlyFetchesInternCandidatesUntilLastPage() throws Exception {
        List<String> requests = new ArrayList<>();
        var result = SejongJobCenterCollector.collect(uri -> {
            requests.add(uri.toString());
            return uri.getPath().endsWith("RecruitView.aspx") ? detail("정보보안", "인턴")
                : listing("22145", "정보보안 인턴", 1) + listing("999", "백엔드 신입", 1);
        });
        assertThat(result).hasSize(1);
        assertThat(requests).hasSize(2).noneMatch(s -> s.contains("rp=2"));
    }
    @Test void rejectsRepeatedPagesAndPropagatesFetchFailureBeforeReturningCandidates() {
        assertThatThrownBy(() -> SejongJobCenterCollector.collect(uri ->
            uri.getPath().endsWith("RecruitView.aspx") ? detail("정보보안", "인턴") : listing("22145", "인턴", 2)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repeated");
        assertThatThrownBy(() -> SejongJobCenterCollector.collect(uri -> { throw new java.io.IOException("blocked"); }))
            .isInstanceOf(java.io.IOException.class);
    }
    @Test void acceptsMoreThanTwentyFinalCandidates() throws Exception {
        StringBuilder rows=new StringBuilder();
        for(int i=1;i<=21;i++) rows.append(listing(Integer.toString(i),"정보보안 인턴",1));
        assertThat(SejongJobCenterCollector.collect(uri ->
            uri.getPath().endsWith("RecruitView.aspx") ? detail("정보보안","인턴") : rows.toString()))
            .hasSize(21);
    }
    @Test void clientRequestsOnlyValidatedTargetWithoutRobotsPreflight() throws Exception {
        List<String> requests=new ArrayList<>();
        var client=new OfficialActivityClient() {
            @Override String request(String url) { requests.add(url);return "offline fixture"; }
        };
        var list=SejongJobCenterParser.listUri(1);
        assertThat(client.fetchJobCenter(list)).isEqualTo("offline fixture");
        assertThat(client.fetchJobCenter(POST.uri())).isEqualTo("offline fixture");
        assertThat(requests).containsExactly(list.toString(),POST.uri().toString());
        assertThatThrownBy(() -> client.fetchJobCenter(java.net.URI.create("https://evil.example/")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).hasSize(2);
    }
    @Test void schoolRequestIntervalRemainsThreeSeconds() {
        assertThat(OfficialActivityClient.requestInterval(SejongJobCenterParser.listUri(1))).isEqualTo(3000);
        assertThat(OfficialActivityClient.requestInterval(POST.uri())).isEqualTo(3000);
    }

    @Test void traversesPastTwentyPagesWithoutInternCandidatesAndStopsAtLastPage() throws Exception {
        List<Integer> pages=new ArrayList<>();
        var rows=SejongJobCenterCollector.collect(uri->{
            int page=Integer.parseInt(uri.getQuery().split("rp=")[1]);pages.add(page);
            return listing(Integer.toString(page),"백엔드 신입",Math.min(page+1,25));
        });
        assertThat(rows).isEmpty();assertThat(pages).hasSize(25).startsWith(1).endsWith(25);
        SejongJobCenterParser.validateUri(SejongJobCenterParser.listUri(25));
        assertThatThrownBy(()->SejongJobCenterParser.listUri(0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void detectsRepeatedPagesWithoutInternCandidates() {
        assertThatThrownBy(()->SejongJobCenterCollector.collect(uri->listing("1","백엔드 신입",25)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repeated");
    }
    @Test void fetchesDuplicateInternDetailOnlyOnceAcrossPages() throws Exception {
        List<String> calls=new ArrayList<>();
        var rows=SejongJobCenterCollector.collect(uri->{
            calls.add(uri.toString());
            if(uri.getPath().endsWith("RecruitView.aspx"))return detail("정보보안","인턴");
            boolean first=uri.getQuery().endsWith("rp=1");
            return listing("1","IT 인턴",2)+(first?"":listing("2","백엔드 신입",2));
        });
        assertThat(rows).hasSize(1);
        assertThat(calls).hasSize(3);
    }

}
