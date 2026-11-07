package com.example.compass.activity.collection;

import java.time.*;
import java.util.Map;

/** detail is used only for the independent course columns; IT evidence is transient. */
public record CollectedActivity(String type,String source,String externalId,String url,String name,String provider,
    String description,String itEvidence,Map<String,String> detail,LocalDate startsOn,LocalDate endsOn,
    OffsetDateTime applicationStartAt,OffsetDateTime applicationEndAt) {
    public CollectedActivity(String type,String source,String externalId,String url,String name,String provider,
        String description,String evidence,Map<String,String> detail){this(type,source,externalId,url,name,provider,description,evidence,Map.copyOf(detail),null,null,null,null);}
}
