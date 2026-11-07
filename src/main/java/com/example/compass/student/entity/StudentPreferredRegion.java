package com.example.compass.student.entity;
import com.example.compass.recruit.entity.Region;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
/** 희망 지역 저장 모델. 연결 코드/ID는 외래키 필드로 설정한다. */
@Entity
@Table(name="student_preferred_region")
public class StudentPreferredRegion {
    @EmbeddedId
    private StudentPreferredRegionId id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="student_id", referencedColumnName="id", insertable=false, updatable=false)
    private Student student;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="region_code", referencedColumnName="code", insertable=false, updatable=false)
    private Region region;

    public StudentPreferredRegion() {}
    public StudentPreferredRegionId getId() { return id; }
    public void setId(StudentPreferredRegionId id) { this.id=id; }
    public Student getStudent() { return student; }
    public Region getRegion() { return region; }
}
