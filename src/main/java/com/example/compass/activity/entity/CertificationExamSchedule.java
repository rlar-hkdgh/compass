package com.example.compass.activity.entity;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
@Entity
@Table(name="certification_exam_schedule")
public class CertificationExamSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;
    @Column(name="certification_code",nullable=false,length=32)
    private String certificationCode;
    @Column(name="exam_year",nullable=false)
    private Integer examYear;
    @Column(name="round_number",nullable=false)
    private Integer roundNumber;
    @Column(name="exam_stage",nullable=false,length=32)
    private String examStage;
    @Column(name="application_starts_on",nullable=true)
    private LocalDate applicationStartsOn;
    @Column(name="application_ends_on",nullable=true)
    private LocalDate applicationEndsOn;
    @Column(name="starts_on",nullable=false)
    private LocalDate startsOn;
    @Column(name="ends_on",nullable=false)
    private LocalDate endsOn;
    public Long getId(){return id;}
    public void setId(Long value){this.id=value;}
    public String getCertificationCode(){return certificationCode;}
    public void setCertificationCode(String value){this.certificationCode=value;}
    public Integer getExamYear(){return examYear;}
    public void setExamYear(Integer value){this.examYear=value;}
    public Integer getRoundNumber(){return roundNumber;}
    public void setRoundNumber(Integer value){this.roundNumber=value;}
    public String getExamStage(){return examStage;}
    public void setExamStage(String value){this.examStage=value;}
    public LocalDate getApplicationStartsOn(){return applicationStartsOn;}
    public void setApplicationStartsOn(LocalDate value){this.applicationStartsOn=value;}
    public LocalDate getApplicationEndsOn(){return applicationEndsOn;}
    public void setApplicationEndsOn(LocalDate value){this.applicationEndsOn=value;}
    public LocalDate getStartsOn(){return startsOn;}
    public void setStartsOn(LocalDate value){this.startsOn=value;}
    public LocalDate getEndsOn(){return endsOn;}
    public void setEndsOn(LocalDate value){this.endsOn=value;}
}
