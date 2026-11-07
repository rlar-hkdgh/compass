package com.example.compass.activity.repository;
import com.example.compass.activity.entity.CertificationExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface CertificationExamScheduleRepository extends JpaRepository<CertificationExamSchedule,Long> {
    Optional<CertificationExamSchedule> findByCertificationCodeAndExamYearAndRoundNumberAndExamStage(String code,Integer year,Integer round,String stage);
    List<CertificationExamSchedule> findByCertificationCodeAndEndsOnGreaterThanEqualOrderByStartsOnAsc(String code,LocalDate date);
}
