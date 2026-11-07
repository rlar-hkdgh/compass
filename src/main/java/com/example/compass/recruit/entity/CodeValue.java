package com.example.compass.recruit.entity;

import jakarta.persistence.*;
@MappedSuperclass
public abstract class CodeValue {
    @Id @Column(length=32) protected String code;
    @Column(nullable=false, length=255) protected String name;
    public String getCode() { return code; }
    public String getName() { return name; }
}
