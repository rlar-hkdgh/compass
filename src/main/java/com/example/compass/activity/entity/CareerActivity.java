package com.example.compass.activity.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
@Entity
@Table(name="career_activity")
public class CareerActivity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;
    @Column(name="activity_type_code",nullable=false,length=32)
    private String activityTypeCode;
    @Column(name="source_code",nullable=false,length=32)
    private String sourceCode;
    @Column(name="source_activity_id",nullable=true,columnDefinition="text")
    private String sourceActivityId;
    @Column(name="name",nullable=false,length=500)
    private String name;
    @Column(name="provider_name",nullable=false,length=255)
    private String providerName;
    @Column(name="description",nullable=false,columnDefinition="text")
    private String description;
    @Column(name="source_url",nullable=true,length=2000)
    private String sourceUrl;
    @Column(name="application_start_at",nullable=true)
    private OffsetDateTime applicationStartAt;
    @Column(name="application_end_at",nullable=true)
    private OffsetDateTime applicationEndAt;
    @Column(name="starts_on",nullable=true)
    private LocalDate startsOn;
    @Column(name="ends_on",nullable=true)
    private LocalDate endsOn;
    public Long getId(){return id;}
    public void setId(Long value){this.id=value;}
    public String getActivityTypeCode(){return activityTypeCode;}
    public void setActivityTypeCode(String value){this.activityTypeCode=value;}
    public String getSourceCode(){return sourceCode;}
    public void setSourceCode(String value){this.sourceCode=value;}
    public String getSourceActivityId(){return sourceActivityId;}
    public void setSourceActivityId(String value){this.sourceActivityId=value;}
    public String getName(){return name;}
    public void setName(String value){this.name=value;}
    public String getProviderName(){return providerName;}
    public void setProviderName(String value){this.providerName=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){this.description=value;}
    public String getSourceUrl(){return sourceUrl;}
    public void setSourceUrl(String value){this.sourceUrl=value;}
    public OffsetDateTime getApplicationStartAt(){return applicationStartAt;}
    public void setApplicationStartAt(OffsetDateTime value){this.applicationStartAt=value;}
    public OffsetDateTime getApplicationEndAt(){return applicationEndAt;}
    public void setApplicationEndAt(OffsetDateTime value){this.applicationEndAt=value;}
    public LocalDate getStartsOn(){return startsOn;}
    public void setStartsOn(LocalDate value){this.startsOn=value;}
    public LocalDate getEndsOn(){return endsOn;}
    public void setEndsOn(LocalDate value){this.endsOn=value;}
}
