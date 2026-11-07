package com.example.compass.activity.collection;

import org.jsoup.nodes.Element;
import java.time.*;
import java.util.regex.Pattern;

final class ActivityDates {
    record Period(LocalDate start,LocalDate end) {}
    record Dodream(Period period,OffsetDateTime applicationStart,OffsetDateTime applicationEnd) {}
    static Dodream dodream(Element root,Element item){
        var times=item.select("form[data-role=topic] p > time[datetime]");LocalDate start=null,end=null;
        if(times.size()==2){start=OffsetDateTime.parse(times.get(0).attr("datetime")).toLocalDate();end=OffsetDateTime.parse(times.get(1).attr("datetime")).toLocalDate();if(end.isBefore(start))throw new IllegalArgumentException("Invalid activity period");}
        // The topic table has separate activity and application columns; never mix them.
        var cells=root.select("#ModuleEcoProgramTopicForm tr td, #ModuleEcoProgramTopicForm li.tbody > span.date");OffsetDateTime a=null,b=null;
        for(Element cell:cells){var ts=cell.select("time[datetime]");if(ts.size()==2&&cell.text().contains("부터")&&cell.text().contains("까지")){if(a!=null) return new Dodream(new Period(start,end),null,null);a=OffsetDateTime.parse(ts.get(0).attr("datetime"));b=OffsetDateTime.parse(ts.get(1).attr("datetime"));}}
        if(a!=null&&b.isBefore(a))throw new IllegalArgumentException("Invalid application period");return new Dodream(new Period(start,end),a,b);
    }
    static OffsetDateTime deadline(String value){
        var m=Pattern.compile("^(20[0-9]{2})-([0-9]{2})-([0-9]{2}) (오전|오후) ([0-9]{1,2}):([0-9]{2}):([0-9]{2})$").matcher(value);
        if(!m.matches())return null;int hour=Integer.parseInt(m.group(5));if(hour<1||hour>12)throw new IllegalArgumentException("Invalid deadline hour");hour=hour%12+(m.group(4).equals("오후")?12:0);
        return LocalDateTime.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),hour,Integer.parseInt(m.group(6)),Integer.parseInt(m.group(7))).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }
    static Period internship(String body){
        var m=Pattern.compile("(?:인턴|실습)\\s*기간\\s*[:：]\\s*(20[0-9]{2})[./-]([0-9]{1,2})[./-]([0-9]{1,2})\\s*~\\s*(20[0-9]{2})[./-]([0-9]{1,2})[./-]([0-9]{1,2})").matcher(body);
        if(!m.find())return new Period(null,null);LocalDate a=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))),b=LocalDate.of(Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),Integer.parseInt(m.group(6)));
        if(b.isBefore(a)||m.find())throw new IllegalArgumentException("Ambiguous internship period");return new Period(a,b);
    }
}
