package com.example.compass.roadmap.service;

import com.example.compass.activity.entity.*;
import com.example.compass.roadmap.entity.*;
import com.example.compass.student.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

/** Persistence of current recommendations only. No AI generation or automatic student activity updates. */
@Service
public class RecommendedTaskService {
    private final EntityManager em;
    public RecommendedTaskService(EntityManager em){this.em=em;}
    public record Draft(Long courseActivityId,Long careerActivityId,String certificationCode,Long certificationExamScheduleId,
        String title,String description,Integer academicYear,String periodType,Integer displayOrder) {}
    @Transactional public void replace(Long studentId,Long gapAnalysisId,List<Draft> drafts){
        lockStudent(studentId);
        GapAnalysis gap=em.find(GapAnalysis.class,gapAnalysisId);
        if(gap==null||!Objects.equals(studentId,gap.getStudentId())||!"SUCCESS".equals(gap.getStatus()))throw new IllegalArgumentException("Successful gap analysis for this student required");
        if(drafts==null||drafts.size()>200)throw new IllegalArgumentException("At most 200 current tasks");
        List<RecommendedTask> rows=new ArrayList<>();Set<String> positions=new HashSet<>();
        for(Draft d:drafts){
            if(d==null||d.title()==null||d.title().isBlank()||d.title().length()>255||d.description()==null||d.description().isBlank()
                ||d.academicYear()==null||d.academicYear()<2000||d.academicYear()>2200||d.displayOrder()==null||d.displayOrder()<0
                ||!Set.of("FIRST_SEMESTER","SUMMER_BREAK","SECOND_SEMESTER","WINTER_BREAK").contains(Objects.toString(d.periodType(),"")))throw new IllegalArgumentException("Invalid task content/period");
            if(!positions.add(d.academicYear()+":"+d.periodType()+":"+d.displayOrder()))throw new IllegalArgumentException("Duplicate task order");
            int links=(d.courseActivityId()==null?0:1)+(d.careerActivityId()==null?0:1)+(d.certificationCode()==null?0:1);
            if(links>1||d.certificationExamScheduleId()!=null&&d.certificationCode()==null)throw new IllegalArgumentException("Conflicting activity links");
            if(d.courseActivityId()!=null&&em.find(CourseActivity.class,d.courseActivityId())==null)throw new IllegalArgumentException("Unknown course");
            if(d.careerActivityId()!=null&&em.find(CareerActivity.class,d.careerActivityId())==null)throw new IllegalArgumentException("Unknown activity");
            if(d.certificationCode()!=null&&em.find(Certification.class,d.certificationCode())==null)throw new IllegalArgumentException("Unknown certification");
            if(d.certificationExamScheduleId()!=null){var exam=em.find(CertificationExamSchedule.class,d.certificationExamScheduleId());if(exam==null||!exam.getCertificationCode().equals(d.certificationCode()))throw new IllegalArgumentException("Exam belongs to a different certification");}
            var row=new RecommendedTask();row.setStudentId(studentId);row.setGapAnalysisId(gapAnalysisId);row.setCourseActivityId(d.courseActivityId());row.setCareerActivityId(d.careerActivityId());row.setCertificationCode(d.certificationCode());row.setCertificationExamScheduleId(d.certificationExamScheduleId());
            row.setTitle(d.title());row.setDescription(d.description());row.setAcademicYear(d.academicYear());row.setPeriodType(d.periodType());row.setDisplayOrder(d.displayOrder());row.setStatus("TODO");rows.add(row);
        }
        em.createQuery("delete from RecommendedTask t where t.studentId=:student").setParameter("student",studentId).executeUpdate();em.flush();
        for(var row:rows)em.persist(row);em.flush();
    }
    @Transactional public void updateStatus(Long studentId,Long taskId,String status){
        lockStudent(studentId);var task=em.find(RecommendedTask.class,taskId);
        if(task==null||!Objects.equals(studentId,task.getStudentId()))throw new IllegalArgumentException("Task does not belong to student");
        if(!Set.of("TODO","IN_PROGRESS","COMPLETED").contains(Objects.toString(status,"")))throw new IllegalArgumentException("Unknown task status");
        if(!status.equals(task.getStatus())){task.setStatus(status);task.setCompletedAt(status.equals("COMPLETED")?OffsetDateTime.now():null);}em.flush();
    }
    private void lockStudent(Long id){if(id==null||em.find(Student.class,id,LockModeType.PESSIMISTIC_WRITE)==null)throw new IllegalArgumentException("Unknown student");}
}
