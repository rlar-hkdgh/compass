package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.collection.ocr.PostingBodyOcr;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Paginated public collection; no login, captcha handling, proxies or automatic retries. */
@Component
public class SaraminCrawler {
    private static final String HOST="https://www.saramin.co.kr";
    private static final String AGENT="SejongCompass/0.1 (capstone public job collection)";
    private final SaraminKeywordClassifier classifier;
    private final PostingBodyOcr bodyOcr;
    public SaraminCrawler(SaraminKeywordClassifier classifier, PostingBodyOcr bodyOcr) {
        this.classifier=classifier; this.bodyOcr=bodyOcr;
    }
    private String robots;
    private long lastRequest;

    public record ListingPage(int number,List<String> postingIds,boolean hasNext) {}

    public ListingPage discoverPage(int page) throws IOException,InterruptedException {
        return parseListingPage(page,fetch(listingPath(page),Map.of()));
    }
    static String listingPath(int page) {
        if(page<1) throw new IllegalArgumentException("시작 페이지는 1 이상이어야 합니다.");
        // The site's entry-level filter includes mixed entry/experienced postings.
        // Experience-independent postings are a separate checkbox (exp_none), not exp_cd=0.
        return "/zf_user/jobs/list/job-category?cat_mcls=2&exp_cd=1&exp_none=y&search_optional_item=y&page="+page
            +"&page_count=100&sort=RD&isAjaxRequest=0";
    }
    static ListingPage parseListingPage(int page,String html) throws IOException {
        Document doc=Jsoup.parse(html,HOST);
        Element wrap=doc.selectFirst("#default_list_wrap");
        if(wrap==null || wrap.selectFirst(".list_recruiting .list_body")==null)
            throw new IOException("공고 목록 구조를 확인할 수 없습니다. 페이지="+page);
        Set<String> ids=new LinkedHashSet<>();
        for(Element item:wrap.select(".list_body .list_item[id^=rec-]")) {
            String id=item.id().substring(4);
            if(!id.matches("[0-9]{1,20}")) throw new IOException("목록 공고번호 형식 변경");
            ids.add(id);
        }
        Element pager=wrap.selectFirst(".PageBox");
        if(pager==null && !ids.isEmpty()) throw new IOException("페이지 탐색 영역이 없습니다.");
        boolean hasNext=false;
        if(pager!=null) {
            Element active=pager.selectFirst(".active");
            if(active==null || !active.text().equals(Integer.toString(page)))
                throw new IOException("요청 페이지와 응답 페이지가 다릅니다. 요청="+page);
            for(Element control:pager.select("button[page],button[onclick],a[href]")) {
                if(control.hasAttr("disabled")) continue;
                String target=control.attr("page");
                if(target.matches("[0-9]+")) hasNext|=Long.parseLong(target)>page;
                var match=Pattern.compile("[?&]page=([0-9]+)(?:[&#'\\\"]|$)")
                    .matcher(control.attr("onclick")+control.attr("href"));
                if(match.find()) hasNext|=Long.parseLong(match.group(1))>page;
            }
        }
        if(ids.isEmpty() && hasNext) throw new IOException("빈 목록과 페이지 표시가 일치하지 않습니다.");
        return new ListingPage(page,List.copyOf(ids),hasNext);
    }
    public SaraminPageParser.Collected collect(String number) throws IOException,InterruptedException {
        if(!number.matches("[0-9]{1,20}")) throw new IllegalArgumentException("숫자 공고번호만 허용합니다.");
        fetch("/zf_user/jobs/relay/view?rec_idx="+number,Map.of());
        String html=fetch("/zf_user/jobs/relay/view-ajax",Map.of("rec_idx",number,"rec_seq","0","view_type","list"));
        Document doc=Jsoup.parse(html,HOST);
        // Require IT major category plus a known IT role, IT technology or IT specialty.
        var originalCodes=SaraminPageParser.originalCodes(doc);
        var jobs=originalCodes.get("cat_kewd");
        try {
            classifier.eligibleItRoles(originalCodes);
        } catch(IllegalArgumentException e) {
            var unknown=classifier.classify(jobs).unknown();
            if(unknown.isEmpty()) throw e;
            var names=SaraminPageParser.keywordNames(doc);
            var labels=unknown.stream().map(code->code+"="+names.getOrDefault(code,"명칭 미확보")).toList();
            throw new IllegalArgumentException(e.getMessage()+" 미확인 태그="+labels,e);
        }
        SaraminRecruitmentMapping.entryExperience(SaraminPageParser.summary(doc).get("경력"));
        Element frame=doc.selectFirst("iframe.iframe_content[src]");
        if(frame==null) throw new IllegalArgumentException("상세 본문이 없습니다(마감/외부/접근 제한 가능).");
        URI uri=URI.create(frame.absUrl("src"));
        if(!"www.saramin.co.kr".equals(uri.getHost()) || !"https".equals(uri.getScheme())
                || !uri.getPath().equals("/zf_user/jobs/relay/view-detail")
                || !number.equals(SaraminPageParser.queryValue(uri.toString(),"rec_idx")))
            throw new IllegalArgumentException("예상하지 못한 상세 주소입니다.");
        String body=fetch(uri.getRawPath()+"?"+uri.getRawQuery(),Map.of());
        return SaraminPageParser.parse(number,html,body,uri.toString(),bodyOcr.supplement(body,uri.toString()));
    }
    private String fetch(String path,Map<String,String> form) throws IOException,InterruptedException {
        if(robots==null) robots=request("/robots.txt",Map.of());
        if(!allowed(robots,path)) throw new IOException("robots.txt 수집 제외 경로: "+path);
        return request(path,form);
    }
    private String request(String path,Map<String,String> form) throws IOException,InterruptedException {
        long wait=3000-(System.currentTimeMillis()-lastRequest);
        if(wait>0) Thread.sleep(wait);
        lastRequest=System.currentTimeMillis();
        Connection req=Jsoup.connect(HOST+path).userAgent(AGENT).timeout(25000)
            .maxBodySize(4_000_000).followRedirects(false).ignoreHttpErrors(true);
        if(path.equals("/zf_user/jobs/relay/view-ajax"))
            req.referrer(HOST+"/zf_user/jobs/relay/view?rec_idx="+form.get("rec_idx"));
        if(!form.isEmpty()) req.method(Connection.Method.POST).data(form);
        Connection.Response response=req.execute();
        // Do not retry or change identity when the site denies a request.
        if(response.statusCode()!=200) throw new HttpFailure(response.statusCode(),path);
        byte[] bytes=response.bodyAsBytes();
        if(bytes.length>=4_000_000) throw new IOException("응답 크기 제한 초과");
        return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
    }
    public static class HttpFailure extends IOException {
        private final int status;
        public HttpFailure(int status,String path) { super("수집 중단 HTTP "+status+": "+path);this.status=status; }
        public int status() { return status; }
    }
    static boolean allowed(String text,String path) {
        boolean matching=false,seenRule=false;
        int best=-1; boolean allow=true;
        for(String raw:text.split("\\R")) {
            String line=raw.split("#",2)[0].trim();
            int colon=line.indexOf(':'); if(colon<0) continue;
            String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT);
            String value=line.substring(colon+1).trim();
            if(key.equals("user-agent")) {
                if(seenRule) { matching=false;seenRule=false; }
                matching|=value.equals("*") || value.equalsIgnoreCase("SejongCompass");
            } else if(key.equals("allow") || key.equals("disallow")) {
                seenRule=true;
                if(!matching || value.isEmpty()) continue;
                String pattern="^"+Pattern.quote(value).replace("*","\\E.*\\Q");
                if(value.endsWith("$")) pattern=pattern.substring(0,pattern.length()-3)+"\\E$";
                if(Pattern.compile(pattern).matcher(path).find() && (value.length()>best || (value.length()==best && key.equals("allow")))) {
                    best=value.length();allow=key.equals("allow");
                }
            }
        }
        return allow;
    }
}

