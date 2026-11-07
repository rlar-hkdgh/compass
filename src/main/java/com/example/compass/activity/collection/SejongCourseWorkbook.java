package com.example.compass.activity.collection;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads the public Korean timetable, not student transcripts or the academic portal. */
public final class SejongCourseWorkbook {
    private SejongCourseWorkbook() {}
    public static List<CollectedActivity> read(Path file,int year,String semester,String sourceUrl) throws Exception {
        if(year!=2026 || !"SECOND_SEMESTER".equals(semester)) throw new IllegalArgumentException("Initial scope is 2026 second semester only");
        OfficialActivityParser.validateTarget(new ActivityCollectionPlan.Target("COURSE","SEJONG_COURSE",sourceUrl,null,null,null,"세종대학교",Map.of()));
        if(java.nio.file.Files.size(file)>32_000_000) throw new IOException("Timetable archive too large");
        try(ZipFile zip=new ZipFile(file.toFile())) {
            List<String> strings=new ArrayList<>();
            if(zip.getEntry("xl/sharedStrings.xml")!=null) {
                var entries=xml(zip,"xl/sharedStrings.xml").getElementsByTagName("si");
                for(int i=0;i<entries.getLength();i++) strings.add(text(entries.item(i),"t"));
            }
            NodeList rows=xml(zip,"xl/worksheets/sheet1.xml").getElementsByTagName("row");
            if(rows.getLength()>10000) throw new IOException("Too many timetable rows");
            Map<String,String> headers=new HashMap<>();Map<String,CollectedActivity> result=new LinkedHashMap<>();
            for(int i=0;i<rows.getLength();i++) {
                Map<String,String> cells=new LinkedHashMap<>();
                NodeList children=((Element)rows.item(i)).getElementsByTagName("c");
                for(int n=0;n<children.getLength();n++) {
                    Element c=(Element)children.item(n);String value=text(c,"v");
                    if("s".equals(c.getAttribute("t"))) value=strings.get(Integer.parseInt(value));
                    else if("inlineStr".equals(c.getAttribute("t"))) value=text(c,"t");
                    if(c.getElementsByTagName("f").getLength()>0) throw new IOException("Formula cells are not accepted as source values");
                    cells.put(c.getAttribute("r").replaceAll("[0-9]",""),value.trim());
                }
                if(i==0) {
                    cells.forEach((k,v)->headers.put(v.replaceAll("\\s+",""),k));
                    for(String h:List.of("개설학과코드","학수번호","분반","교과목명","개설학과전공","학점"))
                        if(!headers.containsKey(h)) throw new IOException("Korean timetable header missing: "+h);
                    continue;
                }
                Map<String,String> row=new LinkedHashMap<>();headers.forEach((h,c)->row.put(h,cells.getOrDefault(c,"")));
                String name=row.getOrDefault("교과목명","");
                String evidence=name+" "+row.getOrDefault("수강대상및유의사항","");
                if(!ActivityItPolicy.accepts("COURSE",name,evidence)) continue;
                String number=row.get("학수번호"),section=row.get("분반"),department=row.get("개설학과코드");
                if(number.isBlank() || section.isBlank()) throw new IOException("IT course lacks course/section ID");
                Map<String,String> detail=new LinkedHashMap<>();
                detail.put("courseNumber",number);detail.put("sectionNumber",section);detail.put("academicYear",Integer.toString(year));detail.put("semester",semester);
                for(var mapping:Map.of("departmentCode","개설학과코드","departmentName","개설학과전공","credits","학점").entrySet()) {
                    String value=row.getOrDefault(mapping.getValue(),"");if(!value.isBlank()) detail.put(mapping.getKey(),value);
                }
                String id=year+":"+semester+":"+department+":"+number+":"+section;
                String description=name+" / "+year+"-2 / "+row.getOrDefault("개설학과전공","")+" / "+row.getOrDefault("수강대상및유의사항","");
                var activity=new CollectedActivity("COURSE","SEJONG_COURSE",id,sourceUrl,name,"세종대학교",description,evidence,Map.copyOf(detail));
                CollectedActivity prior=result.putIfAbsent(id,activity);
                if(prior!=null && !prior.equals(activity)) throw new IOException("Conflicting duplicate timetable identity: "+id);
            }
            return List.copyOf(result.values());
        }
    }
    private static Document xml(ZipFile zip,String name) throws Exception {
        var entry=zip.getEntry(name);if(entry==null) throw new IOException("Missing workbook entry: "+name);
        if(entry.getSize()<0 || entry.getSize()>32_000_000) throw new IOException("Workbook XML too large");
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        try(InputStream input=zip.getInputStream(entry)) { return factory.newDocumentBuilder().parse(input); }
    }
    private static String text(Node node,String tag) {
        NodeList list=((Element)node).getElementsByTagName(tag);StringBuilder out=new StringBuilder();
        for(int i=0;i<list.getLength();i++) out.append(list.item(i).getTextContent());return out.toString();
    }
}
