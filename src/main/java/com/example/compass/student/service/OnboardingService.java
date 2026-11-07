package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.OnboardingDtos;


import com.example.compass.student.entity.OnboardingStep;
import com.example.compass.student.entity.OnboardingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.example.compass.student.dto.OnboardingDtos.*;

@Service
@Transactional
public class OnboardingService {
    private final JdbcTemplate db;
    private final StudentInputService students;
    public OnboardingService(JdbcTemplate db,StudentInputService students) { this.db=db;this.students=students; }

    @Transactional(readOnly=true)
    public View get(long student) {
        students.profile(student);
        var saved=new EnumMap<OnboardingStep,OnboardingStatus>(OnboardingStep.class);
        db.query("select step_code,status from student_onboarding_step where student_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) row->saved.put(OnboardingStep.valueOf(row.getString(1)),OnboardingStatus.valueOf(row.getString(2))),student);
        List<StepState> steps=new ArrayList<>();
        OnboardingStep next=null;
        int completed=0,skipped=0;
        for(var step:OnboardingStep.values()) {
            var status=saved.getOrDefault(step,OnboardingStatus.NOT_STARTED);
            steps.add(new StepState(step,status));
            if(status==OnboardingStatus.COMPLETED) completed++;
            else if(status==OnboardingStatus.SKIPPED) skipped++;
            else if(next==null) next=step;
        }
        return new View(List.copyOf(steps),next,next==null,steps.size(),completed,skipped);
    }

    /** Missing steps are unchanged; each request is atomic, including resetting decisions. */
    public View patch(long student,Patch request) {
        var seen=EnumSet.noneOf(OnboardingStep.class);
        for(var change:request.steps()) {
            if(!seen.add(change.step())) throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","같은 단계는 한 번만 지정하세요.");
        }
        // All student writes share this lock, including concurrent requests from separate sessions.
        students.lock(student);
        for(var change:request.steps()) {
            db.update("delete from student_onboarding_step where student_id=? and step_code=?",student,change.step().name());
            if(change.status()!=OnboardingStatus.NOT_STARTED) {
                db.update("insert into student_onboarding_step(student_id,step_code,status) values(?,?,?)",student,change.step().name(),change.status().name());
            }
        }
        return get(student);
    }
}
