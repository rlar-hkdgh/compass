package com.example.compass.activity.collection;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SejongDodreamTests {
    private String fixture(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/activity/dodream/"+name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private String card(String id,String title) {
        return "<div data-module='eco' data-role='item'><a data-idx='"+id+"' href='/ko/program/all/view/"+id+"'><b class='title'>"+title+"</b></a></div>";
    }
    private String list(String cards,int page,boolean next) {
        return "<div data-module='eco' data-context='program' data-view='list'><form id='ModuleEcoProgramListForm'></form><ul data-role='list'>"+cards+"</ul><div data-role='pagination' data-page='"+page+"'>"
            +(next ? "<a href='/ko/program/all/list/all/1/"+(page+1)+"'>다음</a>" : "")+"</div></div>";
    }
    private String detail(String id,String title,String target,String body) {
        return "<div id='ModuleEcoProgramView'><div data-module='eco' data-role='itemview' data-pidx='"+id+"'>"
            +"<div class='title'><h4>"+title+"</h4><ul><li class='target'><span>"+target+"</span></li></ul></div>"
            +"<div class='info'><div class='department'><a>SW중심대학사업단</a></div></div></div>"
            +"<div class='description'>"+body+"</div></div>";
    }
    @Test void readsRealListWithResponsiveDuplicateTitlesAndPagination() throws Exception {
        var value=SejongDodreamParser.listing(fixture("list.html"),1);
        assertThat(value.allIds()).contains("4343","4306");
        assertThat(value.hasNext()).isTrue();
        assertThat(value.programs()).noneMatch(p->p.title().contains("집현전고전특강"));
    }
    @Test void readsRealCodingProgramAndKeepsIdentityWithoutInferringOpenStatus() throws Exception {
        var program=new SejongDodreamParser.Program("4346","코딩경시대회");
        var row=SejongDodreamParser.detail(program,fixture("detail-code.html")).orElseThrow();
        assertThat(row.externalId()).isEqualTo("4346");
        assertThat(row.applicationStartAt()).isEqualTo(java.time.OffsetDateTime.parse("2026-09-28T10:00:00+09:00"));
        assertThat(row.applicationEndAt()).isEqualTo(java.time.OffsetDateTime.parse("2026-10-18T23:59:59+09:00"));
        assertThat(row.startsOn()).isEqualTo(java.time.LocalDate.parse("2026-10-26"));
        assertThat(row.detail()).isEmpty();
        assertThat(row.provider()).contains("SW");
        assertThat(row.description()).contains("전체 학생","알고리즘");
        assertThat(row.description()).doesNotContain("로그인이 필요합니다","HITS");
    }
    @Test void filtersCardTitlesWithoutUsingDepartmentAsEvidence() {
        var value=SejongDodreamParser.listing(list(card("1","[SW중심대학] 글쓰기 특강")+card("2","코딩 경시대회")+card("3","AI 모델 개발"),1,false),1);
        assertThat(value.programs()).extracting(SejongDodreamParser.Program::id).containsExactly("2","3");
    }
    @Test void rechecksDetailsAndExcludesGraduateOnlyPrograms() {
        var p=new SejongDodreamParser.Program("1","코딩 특강");
        assertThat(SejongDodreamParser.detail(p,detail("1","코딩 특강","대학원생","알고리즘 구현과 코딩 실습 교육을 제공하는 프로그램입니다."))).isEmpty();
        assertThat(SejongDodreamParser.detail(p,detail("1","[SW중심대학] 글쓰기","전체 학생","학술 글쓰기와 발표 방법을 배우는 실습 프로그램입니다."))).isEmpty();
        assertThat(SejongDodreamParser.detail(p,detail("1","코딩 실습","학부생","알고리즘 구현과 코딩 실습 교육을 제공하는 프로그램입니다."))).isPresent();
    }
    @Test void failsClosedOnLoginIdentityMismatchAndChangedLayout() {
        var p=new SejongDodreamParser.Program("1","코딩");
        assertThatThrownBy(()->SejongDodreamParser.listing("<form>로그인</form>",1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SejongDodreamParser.detail(p,"<form>로그인</form>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SejongDodreamParser.detail(p,detail("2","코딩","학부생","코딩 실습"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SejongDodreamParser.listing(list(card("1","코딩"),2,false),1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsExternalOrMismatchedDetailLinksAndUnsafeUris() {
        assertThatThrownBy(()->SejongDodreamParser.listing(list(card("1","코딩").replace("href='/ko","href='https://evil.example/ko"),1,false),1)).isInstanceOf(IllegalArgumentException.class);
        for(String url:List.of("http://do.sejong.ac.kr/ko/program/all/view/1","https://do.sejong.ac.kr/ko/program/all/view/1?extra=1","https://do.sejong.ac.kr/ko/program/all/list/all/1/0"))
            assertThatThrownBy(()->SejongDodreamParser.validateUri(java.net.URI.create(url))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workflowFetchesOnlyItCandidatesUntilLastPage() throws Exception {
        List<String> urls=new ArrayList<>();
        var result=SejongDodreamCollector.collect(uri->{urls.add(uri.toString());return uri.getPath().contains("/view/")
            ? detail("1","코딩","전체 학생","코딩과 알고리즘을 학습하는 실습형 프로그램입니다.")
            : list(card("1","코딩")+card("2","독서 모임"),1,false);});
        assertThat(result).hasSize(1);assertThat(urls).hasSize(2);
    }
    @Test void deduplicatesProgramIdsAcrossPages() throws Exception {
        List<String> urls=new ArrayList<>();
        var result=SejongDodreamCollector.collect(uri->{urls.add(uri.toString());if(uri.getPath().contains("/view/"))return detail("1","코딩","학부생","코딩과 알고리즘을 학습하는 실습형 프로그램입니다.");
            return uri.getPath().endsWith("/1")?list(card("1","코딩"),1,true):list(card("1","코딩")+card("2","독서"),2,false);});
        assertThat(result).hasSize(1);assertThat(urls).hasSize(3);
    }
    @Test void rejectsRepeatedPagesAndAcceptsMoreThanFormerCap() throws Exception {
        assertThatThrownBy(()->SejongDodreamCollector.collect(uri->uri.getPath().contains("/view/")?detail("1","코딩","학부생","코딩과 알고리즘을 학습하는 실습형 프로그램입니다.")
            :list(card("1","코딩"),uri.getPath().endsWith("/1")?1:2,true))).hasMessageContaining("repeated");
        StringBuilder cards=new StringBuilder();for(int i=1;i<=101;i++)cards.append(card(Integer.toString(i),"코딩"));
        assertThat(SejongDodreamCollector.collect(uri->uri.getPath().contains("/view/")?detail(uri.getPath().substring(uri.getPath().lastIndexOf('/')+1),"코딩","학부생","코딩과 알고리즘을 학습하는 실습형 프로그램입니다.")
            :list(cards.toString(),1,false))).hasSize(101);
    }
    @Test void clientKeepsThreeSecondPacingAndValidatedScopeWithoutNetwork() throws Exception {
        List<String> urls=new ArrayList<>();var client=new OfficialActivityClient(){@Override String request(String url){urls.add(url);return "offline";}};
        assertThat(client.fetchDodream(SejongDodreamParser.listUri(1))).isEqualTo("offline");
        assertThat(OfficialActivityClient.requestInterval(SejongDodreamParser.listUri(1))).isEqualTo(3000);
        assertThatThrownBy(()->client.fetchDodream(java.net.URI.create("https://evil.example/"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(urls).hasSize(1);
    }

    @Test void traversesBeyondTwentyPagesAndStopsAtLastPage() throws Exception {
        List<Integer> pages=new ArrayList<>();
        var rows=SejongDodreamCollector.collect(uri->{
            int page=Integer.parseInt(uri.getPath().substring(uri.getPath().lastIndexOf('/')+1));
            pages.add(page); return list(card(Integer.toString(page),"독서 모임"),page,page<25);
        });
        assertThat(rows).isEmpty();assertThat(pages).hasSize(25).startsWith(1).endsWith(25);
        SejongDodreamParser.validateUri(SejongDodreamParser.listUri(25));
        assertThatThrownBy(()->SejongDodreamParser.listUri(0)).isInstanceOf(IllegalArgumentException.class);
    }

}
