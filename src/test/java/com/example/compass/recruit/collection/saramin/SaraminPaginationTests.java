package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.exception.RecruitException;
import com.example.compass.recruit.dto.response.PostingDetail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SaraminPaginationTests {
    private static String listing(int page,String pager) {
        return "<div id='banner_list'><a href='/zf_user/jobs/relay/view?rec_idx=999'>광고</a></div>"
            +"<div id='default_list_wrap'><section class='list_recruiting'><div class='list_body'>"
            +"<div class='list_item' id='rec-123'><a href='/zf_user/jobs/relay/view?rec_idx=456'>추천</a></div>"
            +"</div></section><div class='PageBox'><span class='active'>"+page+"</span>"+pager+"</div></div>";
    }
    @Test void everyListingPageKeepsEntryLevelAndExperienceIndependentFilters() {
        for(int page:List.of(1,2,11)) {
            String url="https://www.saramin.co.kr"+SaraminCrawler.listingPath(page);
            assertThat(SaraminPageParser.queryValue(url,"cat_mcls")).isEqualTo("2");
            assertThat(SaraminPageParser.queryValue(url,"exp_cd")).isEqualTo("1");
            assertThat(SaraminPageParser.queryValue(url,"exp_none")).isEqualTo("y");
            assertThat(SaraminPageParser.queryValue(url,"search_optional_item")).isEqualTo("y");
            assertThat(SaraminPageParser.queryValue(url,"page")).isEqualTo(Integer.toString(page));
            assertThat(SaraminPageParser.queryValue(url,"page_count")).isEqualTo("100");
            assertThat(SaraminPageParser.queryValue(url,"sort")).isEqualTo("RD");
            assertThat(SaraminPageParser.queryValue(url,"isAjaxRequest")).isEqualTo("0");
        }
        assertThatThrownBy(()->SaraminCrawler.listingPath(0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void listUsesNormalItemsAndSupportsNextPageBlockAndLastPage() throws Exception {
        var first=SaraminCrawler.parseListingPage(10,listing(10,
            "<button class='BtnNext' onclick=\"location.href='/zf_user/jobs/list/job-category?page=11&amp;cat_mcls=2'\">다음</button>"));
        assertThat(first.postingIds()).containsExactly("123");
        assertThat(first.hasNext()).isTrue();
        assertThat(SaraminCrawler.parseListingPage(11,listing(11,"<button page='10'>10</button>")).hasNext()).isFalse();
    }
    @Test void changedMarkupAndWrongPageAbortInsteadOfSilentlyEnding() {
        assertThatThrownBy(()->SaraminCrawler.parseListingPage(1,"<h1>Login</h1>")).isInstanceOf(IOException.class);
        assertThatThrownBy(()->SaraminCrawler.parseListingPage(2,listing(1,""))).isInstanceOf(IOException.class);
    }
    static class Pages extends SaraminCrawler {
        Pages() { super(null, null); }
        final Map<Integer,ListingPage> data=new HashMap<>();
        final List<Integer> requested=new ArrayList<>();
        final List<String> collected=new ArrayList<>();
        final Set<String> skipped=new HashSet<>();
        final Map<String,Integer> http=new HashMap<>();
        @Override public ListingPage discoverPage(int number) throws IOException {
            requested.add(number);
            if(!data.containsKey(number)) throw new IOException("Unexpected page "+number);
            return data.get(number);
        }
        @Override public SaraminPageParser.Collected collect(String id) throws IOException {
            collected.add(id);
            if(http.containsKey(id)) throw new HttpFailure(http.get(id),"fixture");
            if(skipped.contains(id)) throw new IllegalArgumentException("텍스트 원문 미확보");
            return SaraminPageParser.parse(id,SaraminCrawlerTests.SUMMARY.replace("123",id),SaraminCrawlerTests.BODY,"fixture");
        }
    }
    static class Saved extends SaraminCollectedPostingStore {
        final List<String> ids=new ArrayList<>();
        final Map<String,Integer> errors=new HashMap<>();
        Saved() { super(null,null,null,null,null); }
        @Override public PostingDetail save(SaraminPageParser.Collected data) {
            if(errors.containsKey(data.number())) throw new RecruitException(errors.get(data.number()),"INVALID_SOURCE_DATA","fixture");
            ids.add(data.number());return null;
        }
    }
    static void run(Pages pages,Saved saved,String... properties) throws Exception {
        var env=new MockEnvironment().withProperty("spring.main.web-application-type","none");
        for(int i=0;i<properties.length;i+=2) env.withProperty("compass.recruit.crawl."+properties[i],properties[i+1]);
        new SaraminCrawlRunner(pages,saved,env,new GenericApplicationContext()).run(new DefaultApplicationArguments());
    }
    @Test void continuesPastSkippedPageAndSavesEachPostingOnceThroughLastPage() throws Exception {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123","456"),true));
        pages.data.put(2,new SaraminCrawler.ListingPage(2,List.of("456","789"),false));
        pages.skipped.addAll(List.of("123","456"));
        run(pages,saved);
        assertThat(pages.requested).containsExactly(1,2);
        assertThat(pages.collected).containsExactly("123","456","789");
        assertThat(saved.ids).containsExactly("789");
    }
    @Test void explicitPageLimitStopsBeforeNextPage() throws Exception {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123"),true));
        run(pages,saved,"max-pages","1");
        assertThat(pages.requested).containsExactly(1);
        assertThat(saved.ids).containsExactly("123");
    }
    @Test void repeatedPageIsAnErrorAndDoesNotDuplicateWrites() {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123"),true));
        pages.data.put(2,new SaraminCrawler.ListingPage(2,List.of("123"),true));
        assertThatThrownBy(()->run(pages,saved)).isInstanceOf(IOException.class).hasMessageContaining("반복");
        assertThat(saved.ids).containsExactly("123");
    }
    @Test void disappearedPostingIsSkippedAndRemainingPostingIsSaved() throws Exception {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123","456"),false));
        pages.http.put("123",404);
        run(pages,saved);
        assertThat(saved.ids).containsExactly("456");
    }
    @Test void rateLimitStopsBatchWithoutProceedingToOtherPostings() {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123","456"),false));
        pages.http.put("123",429);
        assertThatThrownBy(()->run(pages,saved)).isInstanceOf(SaraminCrawler.HttpFailure.class);
        assertThat(pages.collected).containsExactly("123");assertThat(saved.ids).isEmpty();
    }
    @Test void discoveryOnlyWalksPagesWithoutDetailsOrWrites() throws Exception {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(2,new SaraminCrawler.ListingPage(2,List.of("123"),true));
        pages.data.put(3,new SaraminCrawler.ListingPage(3,List.of("456"),false));
        run(pages,saved,"start-page","2","discover-only","true");
        assertThat(pages.requested).containsExactly(2,3);
        assertThat(pages.collected).isEmpty();assertThat(saved.ids).isEmpty();
    }
    @Test void invalidPostingDataDoesNotPreventNextPostingFromSaving() throws Exception {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123","456"),false));
        saved.errors.put("123",400);
        run(pages,saved);
        assertThat(pages.collected).containsExactly("123","456");
        assertThat(saved.ids).containsExactly("456");
    }
    @Test void serverSideFailureStillAbortsInsteadOfSilentlySkipping() {
        var pages=new Pages();var saved=new Saved();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,List.of("123","456"),false));
        saved.errors.put("123",500);
        assertThatThrownBy(()->run(pages,saved)).isInstanceOf(RecruitException.class);
        assertThat(pages.collected).containsExactly("123");
        assertThat(saved.ids).isEmpty();
    }
}

