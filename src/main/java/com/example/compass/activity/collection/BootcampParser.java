package com.example.compass.activity.collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public final class BootcampParser {
    private BootcampParser() {}
    private static final Pattern IT=Pattern.compile("백엔드|프론트엔드|풀스택|클라우드|정보보안|사이버보안|데이터\\s*(분석|엔지니어|사이언)|온디바이스|디지털트윈|로봇\\s*AI|AI\\s*(엔지니어|에이전트)|MLOps|LLM|RAG|프로그래밍|소프트웨어|자바|파이썬",Pattern.CASE_INSENSITIVE);
    private static final String DATE="(20[0-9]{2})\\s*(?:년|[./-])\\s*([0-9]{1,2})\\s*(?:월|[./-])\\s*([0-9]{1,2})(?:\\s*일)?(?:\\s*\\([^)]*\\))?";
    private static final Pattern DATES=Pattern.compile(DATE);
    public record Offer(String source,String id,String label,String url){
        public Offer { validate(source,URI.create(url));if(!id.matches("[1-9][0-9]*")||!URI.create(url).toString().endsWith(source.equals("CODEIT_SPRINT")?"/"+id:"?idx="+id))throw new IllegalArgumentException("Bootcamp identity mismatch"); }
    }
    public static URI listUri(String source){return URI.create(switch(source){case "CODEIT_SPRINT"->"https://sprint.codeit.kr/";case "SOLDESK"->"https://soldesk.com/";default->throw new IllegalArgumentException("Unsupported bootcamp provider");});}
    static void validate(String source,URI uri){
        String pattern=switch(source){case "CODEIT_SPRINT"->"https://sprint\\.codeit\\.kr/(?:admissions/[1-9][0-9]*)?";case "SOLDESK"->"https://soldesk\\.com/(?:shop_view/\\?idx=[1-9][0-9]*)?";default->throw new IllegalArgumentException("Unsupported bootcamp provider");};
        if(!uri.toString().matches(pattern))throw new IllegalArgumentException("Only official bootcamp list/detail URLs are accepted");
    }
    public static List<Offer> listing(String source,String html){
        var doc=Jsoup.parse(html,listUri(source).toString());Map<String,Offer> offers=new LinkedHashMap<>();
        if(source.equals("CODEIT_SPRINT")){
            var links=doc.select("a[href^=/admissions/],a[href^=https://sprint.codeit.kr/admissions/]");
            if(links.isEmpty())throw new IllegalArgumentException("Codeit admission cards missing");
            for(Element link:links){String label=link.text(),url=link.absUrl("href");if(!url.matches("https://sprint\\.codeit\\.kr/admissions/[1-9][0-9]*"))throw new IllegalArgumentException("Unexpected Codeit admission link");
                if(!IT.matcher(label).find()||label.matches(".*(?:모집 예정|공개 예정|모집 마감|모집 종료).*"))continue;
                String id=url.substring(url.lastIndexOf('/')+1);offers.putIfAbsent(id,new Offer(source,id,label,url));
            }
        }else{
            var cards=doc.select(".shop-item");if(cards.isEmpty())throw new IllegalArgumentException("Soldesk course cards missing");
            for(Element card:cards){var headings=card.select(".item-pay h2").eachText().stream().distinct().toList();if(headings.size()!=1)throw new IllegalArgumentException("Soldesk card title missing/ambiguous");
                String title=headings.get(0),text=card.select(".item-detail").text();
                var hours=Pattern.compile("총\\s*([0-9]{3,4})\\s*시간").matcher(text);
                // Long training only; exclude weekend employee short courses, startup and design.
                if(!IT.matcher(title).find()||!hours.find()||Integer.parseInt(hours.group(1))<300||text.matches(".*(?:개강미정|모집종료|모집마감).*"))continue;
                var urls=card.select("a[href]").stream().map(a->a.absUrl("href")).filter(u->u.contains("shop_view/?idx=")).distinct().toList();
                if(urls.size()!=1)throw new IllegalArgumentException("Soldesk card identity ambiguous");String url=urls.get(0);String id=url.substring(url.lastIndexOf('=')+1);offers.putIfAbsent(id,new Offer(source,id,title,url));
            }
        }
        return List.copyOf(offers.values());
    }
    public static Optional<CollectedActivity> detail(Offer offer,String html){
        var doc=Jsoup.parse(html,offer.url());String name,text,evidence;Period education;Application application;
        if(offer.source().equals("CODEIT_SPRINT")){
            Element root=one(doc,"#admission-detail-layout"),guide=one(root,"#admission-detail-course-guide");String guideText=guide.text();
            name=between(guideText,"과정명","모집 일정");if(!IT.matcher(name).find())return Optional.empty();
            for(String token:List.of("Spring","백엔드","프론트엔드","풀스택","데이터","AI"))if(offer.label().toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))&&!name.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Codeit listing/detail course identity differs");
            Element curriculum=one(root,"#admission-detail-curriculum");evidence=curriculum.text();
            education=period(between(guideText,"교육 기간","교육 시간"));application=application(between(guideText,"모집 일정","교육 기간"));
            compareListDates(offer.label(),education);
            text=name+"\n"+guideText+"\n"+curriculum.text();
            for(String selector:List.of("#admission-detail-subjects","#admission-detail-summary","#admission-detail-faq")){var parts=root.select(selector);if(parts.size()>1)throw new IllegalArgumentException("Ambiguous Codeit section");if(parts.size()==1)text+="\n"+parts.first().text();}
        }else{
            String title=one(doc,"#prod_goods_form h1.view_tit").text();if(!title.equals(offer.label()))throw new IllegalArgumentException("Soldesk course identity mismatch");
            var identity=doc.select("script[type=application/ld+json]").stream().anyMatch(s->Pattern.compile("\"@type\"\\s*:\\s*\"Product\"").matcher(s.data()).find()&&Pattern.compile("\"url\"\\s*:\\s*\""+Pattern.quote(offer.url())+"\"").matcher(s.data()).find());
            if(!identity)throw new IllegalArgumentException("Soldesk product URL evidence missing");
            Element body=one(doc,"#prodDetailPC");String training=body.text();if(!IT.matcher(title+" "+training).find())return Optional.empty();
            name=title;text=title+"\n"+one(doc,"#prod_goods_form .goods_summary").text()+"\n"+training;evidence=training;
            education=labeledPeriod(training,"본\\s*교육\\s*일정");application=labeledApplication(training);
            var headings=Pattern.compile("20[0-9]{2}년\\s*[0-9]{1,2}월\\s*[0-9]{1,2}일\\s*개강").matcher(one(doc,"#prod_goods_form .goods_summary").text());
            if(headings.find()&&education.start()!=null&&!dates(headings.group()).get(0).equals(education.start()))throw new IllegalArgumentException("Soldesk opening dates conflict");
        }
        if(name.isBlank()||name.length()>500||text.length()<40||text.length()>200_000)throw new IllegalArgumentException("Invalid bootcamp detail");
        if(!ActivityItPolicy.accepts("BOOTCAMP",name,evidence))return Optional.empty();
        return Optional.of(new CollectedActivity("BOOTCAMP",offer.source(),offer.id(),offer.url(),name,offer.source().equals("CODEIT_SPRINT")?"코드잇 스프린트":"솔데스크",text,evidence,Map.of(),education.start(),education.end(),application.start(),application.end()));
    }
    record Period(LocalDate start,LocalDate end) {}
    record Application(OffsetDateTime start,OffsetDateTime end) {}
    private static Element one(Element root,String selector){var es=root.select(selector);if(es.size()!=1)throw new IllegalArgumentException("Missing/ambiguous bootcamp field: "+selector);return es.first();}
    private static String between(String text,String a,String b){int start=text.indexOf(a),end=text.indexOf(b,start+a.length());if(start<0||end<0)throw new IllegalArgumentException("Bootcamp label missing: "+a);return text.substring(start+a.length(),end).trim();}
    private static List<LocalDate> dates(String text){var m=DATES.matcher(text);List<LocalDate> result=new ArrayList<>();while(m.find())result.add(LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))));return result;}
    private static Period period(String text){List<LocalDate> values=dates(text);if(values.isEmpty()&&text.matches(".*(?:미정|예정|추후|공개).*"))return new Period(null,null);if(values.size()!=2||values.get(1).isBefore(values.get(0)))throw new IllegalArgumentException("Invalid education period");return new Period(values.get(0),values.get(1));}
    private static Period labeledPeriod(String text,String label){var m=Pattern.compile(label+"\\s*("+DATE+"\\s*[~—–-]\\s*"+DATE+")").matcher(text);Set<Period> periods=new HashSet<>();while(m.find())periods.add(period(m.group(1)));if(periods.size()>1)throw new IllegalArgumentException("Conflicting education periods");if(periods.isEmpty())throw new IllegalArgumentException("Main training period missing");return periods.iterator().next();}
    private static Application application(String text){var values=dates(text);if(values.isEmpty())return new Application(null,null);if(values.size()>2)throw new IllegalArgumentException("Ambiguous application dates");
        boolean startOnly=values.size()==1&&text.contains("부터");
        if(values.size()==1&&!startOnly&&!text.matches(".*(?:까지|마감).*"))return new Application(null,null);
        LocalDate first=values.size()==2||startOnly?values.get(0):null,last=values.get(values.size()-1);if(first!=null&&last.isBefore(first))throw new IllegalArgumentException("Invalid application period");
        // Date-only notices are normalized to KST calendar boundaries, not claimed exact source times.
        OffsetDateTime start=first==null?null:first.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),end=startOnly?null:last.atTime(23,59,59).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        var time=Pattern.compile("([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?").matcher(text);List<LocalTime> times=new ArrayList<>();while(time.find())times.add(LocalTime.of(Integer.parseInt(time.group(1)),Integer.parseInt(time.group(2)),time.group(3)==null?0:Integer.parseInt(time.group(3))));
        if(times.size()>2)throw new IllegalArgumentException("Ambiguous application times");if((times.size()==2||startOnly&&!times.isEmpty())&&first!=null)start=first.atTime(times.get(0)).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();if(!startOnly&&!times.isEmpty())end=last.atTime(times.get(times.size()-1)).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        if(start!=null&&end!=null&&end.isBefore(start))throw new IllegalArgumentException("Invalid application timestamps");return new Application(start,end);
    }
    private static Application labeledApplication(String text){Set<Application> result=new HashSet<>();var m=Pattern.compile("(?:모집\\s*기간|신청\\s*기간|접수\\s*기간)\\s*[:：]?\\s*([^\\n]{0,160})").matcher(text);while(m.find()){
            String rest=m.group(1);var boundary=Pattern.compile("본\\s*교육|기초\\s*교육|교육\\s*(?:기간|일정|정보|장소)|자비부담금").matcher(rest);if(boundary.find())rest=rest.substring(0,boundary.start());
            var parsed=application(rest);if(parsed.start()!=null||parsed.end()!=null)result.add(parsed);}
        if(result.size()>1)throw new IllegalArgumentException("Conflicting application periods");if(!result.isEmpty())return result.iterator().next();
        var deadline=Pattern.compile("(?:모집\\s*마감|신청\\s*마감|접수\\s*마감)\\s*[:：]?\\s*("+DATE+"(?:\\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?)").matcher(text);Set<Application> deadlines=new HashSet<>();while(deadline.find())deadlines.add(application(deadline.group(1)+" 마감"));if(deadlines.size()>1)throw new IllegalArgumentException("Conflicting application deadlines");return deadlines.isEmpty()?new Application(null,null):deadlines.iterator().next();
    }
    private static void compareListDates(String label,Period period){var m=Pattern.compile("([0-9]{2})\\.([0-9]{2})\\.([0-9]{2})\\s*~\\s*([0-9]{2})\\.([0-9]{2})\\.([0-9]{2})").matcher(label);if(m.find()&&period.start()!=null){if(period.start().getYear()%100!=Integer.parseInt(m.group(1))||period.start().getMonthValue()!=Integer.parseInt(m.group(2))||period.start().getDayOfMonth()!=Integer.parseInt(m.group(3))||period.end().getYear()%100!=Integer.parseInt(m.group(4))||period.end().getMonthValue()!=Integer.parseInt(m.group(5))||period.end().getDayOfMonth()!=Integer.parseInt(m.group(6)))throw new IllegalArgumentException("Listing and detailed education periods conflict");}}
}
