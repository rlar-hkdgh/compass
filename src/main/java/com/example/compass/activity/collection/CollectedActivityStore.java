package com.example.compass.activity.collection;

import com.example.compass.activity.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CollectedActivityStore {
    private final EntityManager em;
    public CollectedActivityStore(EntityManager em){this.em=em;}
    @Transactional public Long save(CollectedActivity input){
        OfficialActivityParser.validateTarget(new ActivityCollectionPlan.Target(input.type(),input.source(),input.url(),null,null,null,input.provider(),Map.of()));
        if(!ActivityItPolicy.accepts(input.type(),input.name(),input.itEvidence()))throw new IllegalArgumentException("Not an evidenced IT activity");
        if(input.externalId()==null||input.externalId().isBlank()||input.name()==null||input.name().isBlank()||input.name().length()>500||input.description()==null||input.description().isBlank()||input.description().length()>200_000)throw new IllegalArgumentException("Invalid activity content");
        if(input.type().equals("COURSE"))return saveCourse(input);
        if(em.find(ActivitySource.class,input.source())==null)throw new IllegalArgumentException("Unknown source");
        if(input.detail()!=null&&!input.detail().isEmpty())throw new IllegalArgumentException("General activity details belong in description");
        var rows=em.createQuery("select a from CareerActivity a where a.sourceCode=:source and a.sourceActivityId=:id",CareerActivity.class).setParameter("source",input.source()).setParameter("id",input.externalId()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        var row=rows.isEmpty()?new CareerActivity():rows.get(0);
        if(row.getId()!=null&&!row.getActivityTypeCode().equals(input.type()))throw new IllegalArgumentException("Existing activity type cannot change");
        row.setActivityTypeCode(input.type());row.setSourceCode(input.source());row.setSourceActivityId(input.externalId());row.setName(input.name());row.setProviderName(input.provider());row.setDescription(input.description());row.setSourceUrl(input.url());
        if(input.startsOn()!=null)row.setStartsOn(input.startsOn());if(input.endsOn()!=null)row.setEndsOn(input.endsOn());if(input.applicationStartAt()!=null)row.setApplicationStartAt(input.applicationStartAt());if(input.applicationEndAt()!=null)row.setApplicationEndAt(input.applicationEndAt());
        if(row.getStartsOn()!=null&&row.getEndsOn()!=null&&row.getEndsOn().isBefore(row.getStartsOn())||row.getApplicationStartAt()!=null&&row.getApplicationEndAt()!=null&&row.getApplicationEndAt().isBefore(row.getApplicationStartAt()))throw new IllegalArgumentException("Invalid activity period");
        if(row.getId()==null)em.persist(row);em.flush();return row.getId();
    }
    private Long saveCourse(CollectedActivity input){
        var d=input.detail();if(d==null||!Set.of("courseNumber","academicYear","semester","sectionNumber","departmentCode","departmentName","credits").containsAll(d.keySet()))throw new IllegalArgumentException("Unsupported course fields");
        for(String field:List.of("courseNumber","academicYear","semester","sectionNumber","departmentCode"))if(d.getOrDefault(field,"").isBlank())throw new IllegalArgumentException("Missing course identity: "+field);
        int year=Integer.parseInt(d.get("academicYear"));String number=d.get("courseNumber"),semester=d.get("semester"),section=d.get("sectionNumber"),department=d.get("departmentCode");
        if(!input.externalId().equals(year+":"+semester+":"+department+":"+number+":"+section))throw new IllegalArgumentException("Course identity mismatch");
        var rows=em.createQuery("select c from CourseActivity c where c.academicYear=:year and c.semester=:semester and c.courseNumber=:number and c.sectionNumber=:section and c.departmentCode=:department",CourseActivity.class)
            .setParameter("year",year).setParameter("semester",semester).setParameter("number",number).setParameter("section",section).setParameter("department",department).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        var row=rows.isEmpty()?new CourseActivity():rows.get(0);row.setCourseNumber(number);row.setAcademicYear(year);row.setSemester(semester);row.setSectionNumber(section);row.setDepartmentCode(department);row.setName(input.name());row.setDescription(input.description());row.setSourceUrl(input.url());
        if(d.containsKey("departmentName"))row.setDepartmentName(d.get("departmentName"));if(d.containsKey("credits"))row.setCredits(new BigDecimal(d.get("credits")));
        if(row.getId()==null)em.persist(row);em.flush();return row.getId();
    }
}
