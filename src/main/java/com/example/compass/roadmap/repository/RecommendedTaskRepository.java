package com.example.compass.roadmap.repository;
import com.example.compass.roadmap.entity.RecommendedTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RecommendedTaskRepository extends JpaRepository<RecommendedTask,Long> {
 @org.springframework.data.jpa.repository.Query("select t from RecommendedTask t where t.studentId=:studentId order by t.academicYear, case t.periodType when 'FIRST_SEMESTER' then 1 when 'SUMMER_BREAK' then 2 when 'SECOND_SEMESTER' then 3 else 4 end, t.displayOrder")
 List<RecommendedTask> findCurrentByStudentId(@org.springframework.data.repository.query.Param("studentId") Long studentId);
}
