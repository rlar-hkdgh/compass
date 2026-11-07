package com.example.compass.activity.collection;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.*;
import java.util.*;

@Component
public class BootcampCollector {
    private final OfficialActivityClient client;
    public BootcampCollector(OfficialActivityClient client){this.client=client;}
    @FunctionalInterface interface PageReader{String read(String source,URI uri)throws Exception;}
    public List<CollectedActivity> collect()throws Exception{return collect(LocalDate.now(ZoneId.of("Asia/Seoul")),client::fetchBootcamp);}
    static List<CollectedActivity> collect(LocalDate today,PageReader reader)throws Exception{
        List<BootcampParser.Offer> offers=new ArrayList<>();
        for(String source:List.of("CODEIT_SPRINT","SOLDESK"))offers.addAll(BootcampParser.listing(source,reader.read(source,BootcampParser.listUri(source))));
        Set<String> seen=new HashSet<>();List<CollectedActivity> result=new ArrayList<>();
        for(var offer:offers){
            if(!seen.add(offer.source()+":"+offer.id()))continue;
            var parsed=BootcampParser.detail(offer,reader.read(offer.source(),URI.create(offer.url())));
            if(parsed.isEmpty())continue;var row=parsed.get();
            // Unknown education dates are not fabricated; old/in-progress cohorts are excluded.
            if(row.startsOn()==null||row.endsOn()==null||row.startsOn().isBefore(today)
                ||row.applicationEndAt()!=null&&row.applicationEndAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate().isBefore(today))continue;
            result.add(row);
        }
        return List.copyOf(result);
    }
}
