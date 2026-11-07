package com.example.compass.recruit.entity;

import jakarta.persistence.*;
@Entity @Table(name="company")
public class Company {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=255) private String name;
    @Column(length=2000) private String companyInfoUrl;
    protected Company() {}
    public Company(String name, String companyInfoUrl) { this.name=name; this.companyInfoUrl=companyInfoUrl; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCompanyInfoUrl() { return companyInfoUrl; }
}
