package com.example.compass.student.entity;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 전공 이수 구분 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="major_completion_type")
public class MajorCompletionType {
    /** 주전공·복수전공·제2전공·부전공·연계전공·융합전공 */
    @Id
    @Column(name="code", nullable=false, length=32)
    private String code;
    /** 이수 구분명 */
    @Column(name="name", nullable=false, length=100)
    private String name;

    public MajorCompletionType() {}
    public String getCode() { return code; }
    public void setCode(String value) { this.code=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
}
