package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 전공 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="major")
public class Major {
    /** 확인된 최신 학과·전공 코드 */
    @Id
    @Column(name="code", nullable=false, length=32)
    private String code;
    /** 최신 전공명 */
    @Column(name="name", nullable=false, length=255)
    private String name;
    /** 소속 대학 */
    @Column(name="college_name", nullable=true, length=255)
    private String collegeName;

    public Major() {}
    public String getCode() { return code; }
    public void setCode(String value) { this.code=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String value) { this.collegeName=value; }
}
