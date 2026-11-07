package com.example.compass.activity.collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;

public final class OfficialActivityParser {
    private OfficialActivityParser() {}
    private static final Map<String,Set<String>> HOSTS=Map.ofEntries(
        Map.entry("SEJONG_COURSE",Set.of("www.sejong.ac.kr")),Map.entry("SEJONG_DODREAM",Set.of("do.sejong.ac.kr")),
        Map.entry("SEJONG_JOB_CENTER",Set.of("www.sejong.ac.kr","dept.sejong.ac.kr","udream.sejong.ac.kr")),
        Map.entry("CODEIT_SPRINT",Set.of("sprint.codeit.kr")),Map.entry("SOLDESK",Set.of("soldesk.com")),
        Map.entry("WORK24",Set.of("www.work24.go.kr","m.work24.go.kr")),Map.entry("SSAFY",Set.of("www.ssafy.com")),
        Map.entry("BOOSTCAMP",Set.of("boostcamp.connect.or.kr")),Map.entry("WOOWACOURSE",Set.of("woowacourse.io","www.woowacourse.io")));
    private static final Map<String,String> TYPES=Map.of("CODEIT_SPRINT","BOOTCAMP","SOLDESK","BOOTCAMP","SEJONG_COURSE","COURSE","SEJONG_DODREAM","EXTRACURRICULAR","SEJONG_JOB_CENTER","INTERNSHIP",
        "WORK24","BOOTCAMP","SSAFY","BOOTCAMP","BOOSTCAMP","BOOTCAMP","WOOWACOURSE","BOOTCAMP");
    public static URI validateTarget(ActivityCollectionPlan.Target t){
        Objects.requireNonNull(t,"target");if(!Objects.equals(TYPES.get(t.source()),t.type())||!TYPES.containsKey(t.source()))throw new IllegalArgumentException("Unsupported source/type; certifications use manual SQL");
        URI uri=URI.create(t.url());if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!HOSTS.get(t.source()).contains(uri.getHost()))throw new IllegalArgumentException("Only allowlisted official HTTPS URLs are accepted");
        if(Set.of("CODEIT_SPRINT","SOLDESK").contains(t.source()))BootcampParser.validate(t.source(),uri);
        if("udream.sejong.ac.kr".equals(uri.getHost()))SejongJobCenterParser.validateUri(uri);
        if(t.provider()==null||t.provider().isBlank()||t.provider().length()>255)throw new IllegalArgumentException("Provider required");return uri;
    }
    public static Optional<CollectedActivity> parse(ActivityCollectionPlan.Target t,String html){
        URI uri=validateTarget(t);var doc=Jsoup.parse(html,uri.toString());var content=one(doc,t.contentSelector());
        String name=one(content,t.titleSelector()).text(),text=content.text(),evidence=one(content,t.evidenceSelector()).text();
        if(name.isBlank()||name.length()>500||text.length()<20||text.length()>200_000)throw new IllegalArgumentException("Invalid activity content");
        if(!ActivityItPolicy.accepts(t.type(),name,evidence))return Optional.empty();
        var detail=t.detail()==null?Map.<String,String>of():t.detail();if(!Set.of("companyName","programId").containsAll(detail.keySet()))throw new IllegalArgumentException("Only source identity hints remain; detail content belongs in description");
        String id=uri.toString();
        if(t.type().equals("EXTRACURRICULAR")){
            var m=java.util.regex.Pattern.compile("/view/([0-9]+)(?:/|$)").matcher(uri.getPath());if(!m.find()||!m.group(1).equals(detail.get("programId")))throw new IllegalArgumentException("Program ID differs from official URL");id=m.group(1);
        }
        if(t.type().equals("INTERNSHIP")){
            if(!text.contains("대학일자리")&&!text.contains("취업지원"))throw new IllegalArgumentException("School repost must identify the career center");
            String company=detail.getOrDefault("companyName","");if(company.isBlank()||!text.replaceAll("\\s+","").contains(company.replaceAll("\\s+","")))throw new IllegalArgumentException("Company lacks page evidence");
            var m=java.util.regex.Pattern.compile("(?:^|&)articleNo=([0-9]+)(?:&|$)").matcher(Objects.toString(uri.getRawQuery(),""));if(!m.find())throw new IllegalArgumentException("School post articleNo required");id=uri.getHost()+uri.getPath()+":"+m.group(1)+":"+name+":"+company;
        }
        return Optional.of(new CollectedActivity(t.type(),t.source(),id,uri.toString(),name,t.provider(),text,evidence,Map.of()));
    }
    private static Element one(Element root,String selector){if(selector==null||selector.isBlank()||Set.of("html","body","*").contains(selector.trim()))throw new IllegalArgumentException("A specific content selector is required");var elements=root.select(selector);if(elements.size()!=1)throw new IllegalArgumentException("Expected one content element: "+selector);return elements.first();}
}
