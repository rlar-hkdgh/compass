package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.collection.ocr.PostingBodyOcr;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Parses only the requested posting's public detail, never recommendation cards. */
public class SaraminPageParser {
    public record Collected(String number, String title, String companyName, String companyUrl,
        String bodyUrl, String description, boolean active, Map<String,String> summary,
        Map<String,List<String>> codes, Instant applicationStart, Instant applicationEnd,
        String deadlineTypeCode, String deadlineEvidence,Map<String,String> keywordNames, List<PostingBodyOcr.Evidence> bodyOcr) {}

    public static Collected parse(String number, String summaryHtml, String bodyHtml, String bodyUrl) {
        return parse(number, summaryHtml, bodyHtml, bodyUrl, new PostingBodyOcr.Result(bodyHtml, List.of()));
    }
    public static Collected parse(String number, String summaryHtml, String bodyHtml, String bodyUrl,
                                  PostingBodyOcr.Result supplemented) {
        Document original=Jsoup.parse(bodyHtml);
        original.select("script,style,noscript,iframe,button").remove();
        Element originalContent=original.selectFirst(".user_content");
        String originalText=(originalContent==null ? original.body() : originalContent).text();
        Document page=Jsoup.parse(summaryHtml, "https://www.saramin.co.kr");
        if(!number.matches("[0-9]{1,20}")) throw new IllegalArgumentException("잘못된 공고번호");
        Element header=page.selectFirst(".jv_header[data-rec_idx="+number+"]");
        if(header==null) throw new IllegalArgumentException("요청 공고의 상세 헤더가 없습니다: "+number);
        Element title=header.selectFirst("h1.tit_job");
        Element company=header.selectFirst("a.company");
        if(title==null || title.text().isBlank() || company==null || company.text().isBlank())
            throw new IllegalArgumentException("공고 제목 또는 회사명이 없습니다.");
        Document body=Jsoup.parse(supplemented.html());
        body.select("script,style,noscript,iframe,button").remove();
        Element content=body.selectFirst(".user_content");
        if(content==null) content=body.body();
        // Original HTML alone drives structured fields; OCR only supplements description.
        content.select("br").before("\n");
        content.select("p,div,li,tr,h1,h2,h3,h4").before("\n");
        String description=content.wholeText().replace('\u00a0',' ')
            .replaceAll("[ \t]+"," ").replaceAll(" *\n *","\n").replaceAll("\n{3,}","\n\n").trim();
        if(description.length()<80) throw new IllegalArgumentException("텍스트 상세가 부족합니다(이미지/외부 페이지 공고 가능).");
        Map<String,String> summary=summary(page);
        var codes=originalCodes(page);
        Instant start=null,end=null;
        String closingText="";
        for(Element dt:page.select(".info_period dt")) {
            Element dd=dt.nextElementSibling();
            if(dd==null || !dd.tagName().equals("dd")) continue;
            Instant date=date(dd.text());
            if(dt.text().contains("시작")) start=date;
            if(dt.text().contains("마감")) { end=date; closingText=dd.text(); }
        }
        Element apply=header.selectFirst(".btn_apply button:not([disabled])");
        boolean active=apply!=null && !apply.text().matches(".*(마감|종료).*" );
        if(end!=null && end.isBefore(Instant.now())) active=false;
        var deadline=deadline(closingText,originalText,end);
        return new Collected(number,title.text(),company.text(),optionalCompanyUrl(company),bodyUrl,
            description,active,summary,codes,start,end,deadline.code(),deadline.evidence(),keywordNames(page),supplemented.evidence());
    }
    static Map<String,String> summary(Document page) {
        Map<String,String> summary=new LinkedHashMap<>();
        for(Element dl:page.select(".jv_summary dl")) {
            Element dt=dl.selectFirst("dt"),dd=dl.selectFirst("dd");
            if(dt!=null && dd!=null) {
                Element clean=dd.clone();clean.select("button,.toolTipWrap").remove();
                summary.put(dt.text(),clean.text());
            }
        }
        return summary;
    }
    private static String optionalCompanyUrl(Element company) {
        String href=company.attr("href").trim();
        if(href.isEmpty() || href.startsWith("#")) return null;
        String value=company.absUrl("href");
        try {
            URI uri=URI.create(value);
            if(value.length()<=2000 && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost()!=null && uri.getUserInfo()==null) return value;
        } catch(IllegalArgumentException ignored) {}
        return null;
    }
    static Map<String,List<String>> originalCodes(Document page) {
        Map<String,List<String>> codes=new LinkedHashMap<>();
        for(String key:List.of("cat_mcls","cat_kewd","loc_cd","ind_cd")) {
            Set<String> values=new LinkedHashSet<>();
            for(Element a:page.select(".jv_footer .tags a[href]")) {
                String value=queryValue(a.absUrl("href"),key);
                if(value!=null) for(String part:value.split(",")) if(part.matches("[0-9]+")) values.add(part);
            }
            codes.put(key,List.copyOf(values));
        }
        return codes;
    }
    static Map<String,String> keywordNames(Document page) {
        Map<String,String> names=new LinkedHashMap<>();
        for(Element link:page.select(".jv_footer .tags a[href]")) {
            String codes=queryValue(link.absUrl("href"),"cat_kewd");
            if(codes==null) continue;
            String label=link.text().replaceFirst("^#", "").trim();
            for(String code:codes.split(",")) if(code.matches("[0-9]+")) names.putIfAbsent(code,label);
        }
        return names;
    }
    record Deadline(String code,String evidence) {}
    static Deadline deadline(String closingText,String description,Instant end) {
        // Dedicated closing text takes precedence; explicit body policy outranks a date.
        var dedicated=explicitDeadline(closingText);
        if(!dedicated.isEmpty()) return singleDeadline(dedicated);
        var body=explicitDeadline(description);
        if(!body.isEmpty()) return singleDeadline(body);
        return end==null ? new Deadline(null,null) : new Deadline("1","접수 마감일 표시: "+closingText);
    }
    private static Map<String,String> explicitDeadline(String text) {
        Map<String,String> matches=new LinkedHashMap<>();
        Map<String,String> patterns=Map.of("2","채용\\s*시(?:\\s*마감|\\s*까지|$)",
            "3","상시\\s*(?:채용|모집|접수)","4","수시\\s*(?:채용|모집|접수)");
        for(var entry:patterns.entrySet()) {
            var match=Pattern.compile(entry.getValue()).matcher(text);
            if(match.find()) matches.put(entry.getKey(),match.group());
        }
        return matches;
    }
    private static Deadline singleDeadline(Map<String,String> matches) {
        if(matches.size()!=1) return new Deadline(null,"마감 방식 표현 충돌: "+String.join(",",matches.values()));
        var match=matches.entrySet().iterator().next();
        return new Deadline(match.getKey(),match.getValue());
    }
    static String queryValue(String url,String key) {
        try {
            String query=URI.create(url).getRawQuery();
            if(query==null) return null;
            for(String part:query.split("&")) {
                String[] pair=part.split("=",2);
                if(pair.length==2 && pair[0].equals(key)) return URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
            }
        } catch(IllegalArgumentException ignored) {}
        return null;
    }
    private static Instant date(String text) {
        var m=Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}").matcher(text);
        if(!m.find()) return null;
        return LocalDateTime.parse(m.group(),DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
            .atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }
}

