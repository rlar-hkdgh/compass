package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 자격증 목록 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="certification")
public class Certification {
    /** 운영자가 부여하는 서비스 자격증 코드 */
    @Id
    @Column(name="code", nullable=false, length=32)
    private String code;
    /** 자격증명 */
    @Column(name="name", nullable=false, length=255)
    private String name;
    /** 발급기관 */
    @Column(name="issuing_organization", nullable=false, length=255)
    private String issuingOrganization;

    public Certification() {}
    public String getCode() { return code; }
    public void setCode(String value) { this.code=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getIssuingOrganization() { return issuingOrganization; }
    public void setIssuingOrganization(String value) { this.issuingOrganization=value; }
}
