package com.example.compass.activity.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
@Entity
@Table(name="activity_source")
public class ActivitySource {
    @Id
    @Column(name="code",nullable=false,length=32)
    private String code;
    @Column(name="name",nullable=false,length=100)
    private String name;
    @Column(name="base_url",nullable=true,length=2000)
    private String baseUrl;
    public String getCode(){return code;}
    public void setCode(String value){this.code=value;}
    public String getName(){return name;}
    public void setName(String value){this.name=value;}
    public String getBaseUrl(){return baseUrl;}
    public void setBaseUrl(String value){this.baseUrl=value;}
}
