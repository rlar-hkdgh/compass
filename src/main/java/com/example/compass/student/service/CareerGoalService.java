package com.example.compass.student.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import static com.example.compass.student.dto.CareerGoalDtos.*;
import com.example.compass.student.exception.ApiErrors;

@Service @Transactional
public class CareerGoalService {
    private static final String COMPANY_SIZES_TABLE = "student_career_goal_company_size";
    private final JdbcTemplate db;
    private final StudentInputService students;
    public CareerGoalService(JdbcTemplate db,StudentInputService students){this.db=db;this.students=students;}
    @Transactional(readOnly=true)
    public CareerGoal get(long student){
        students.profile(student);
        return db.query("select * from student_career_goal where student_id=?", (r,n) -> {
            var sizes=students.selected(COMPANY_SIZES_TABLE,"company_size","student_id",student)
                .stream().map(CompanySize::valueOf).toList();
            // A single legacy selection is also readable during a rolling upgrade.
            if(sizes.isEmpty()) sizes=List.of(CompanySize.valueOf(r.getString("company_size")));
            String careerType=r.getString("career_type");
            return new CareerGoal(r.getString("job_category_code"),r.getString("job_field"),sizes.get(0),sizes,
                r.getLong("target_salary"),students.selected("student_preferred_region","region_code","student_id",student),
                careerType==null?null:CareerType.valueOf(careerType),r.getString("target_company"),r.getString("target_role"),
                YearMonth.from(r.getObject("target_date",LocalDate.class)).toString());
        },student).stream().findFirst().orElseThrow(ApiErrors::missing);
    }
    public CareerGoal save(long student,CareerGoalInput q){
        students.lock(student);
        students.code("job_category",q.jobCategoryCode());
        students.codes("region",q.regionCodes());
        var sizes=q.companySizes();
        if(sizes==null && q.companySize()!=null) sizes=List.of(q.companySize());
        if(sizes==null || sizes.isEmpty()) throw ApiErrors.invalid("기업 규모를 하나 이상 선택하세요.");
        if(new HashSet<>(sizes).size()!=sizes.size())
            throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","기업 규모를 중복 선택할 수 없습니다.");
        if(q.targetRole()!=null && q.targetRole().isBlank()) throw ApiErrors.invalid("목표 직무명은 공백일 수 없습니다.");

        var sizeCodes=sizes.stream().map(CompanySize::name).sorted().toList();
        String legacyCompanySize=sizeCodes.stream().filter(code -> !"SMALL".equals(code)).findFirst().orElse(null);
        String careerType=q.careerType()==null?null:q.careerType().name();
        var date=YearMonth.parse(q.targetDate()).atDay(1);
        int updated=db.update("""
            update student_career_goal set job_category_code=?,company_size=?,target_salary=?,
                career_type=coalesce(?,career_type),target_company=?,target_role=coalesce(?,target_role),target_date=?
                where student_id=?
            """,q.jobCategoryCode(),legacyCompanySize,q.targetSalary(),careerType,q.targetCompany(),q.targetRole(),date,student);
        if(updated==0){
            db.update("""
                insert into student_career_goal(student_id,job_category_code,company_size,target_salary,career_type,target_company,target_role,target_date)
                values(?,?,?,?,?,?,?,?)
                """,student,q.jobCategoryCode(),legacyCompanySize,q.targetSalary(),careerType,
                q.targetCompany(),q.targetRole(),date);
        }
        students.replace(COMPANY_SIZES_TABLE,"company_size","student_id",student,sizeCodes);
        students.replace("student_preferred_region","region_code","student_id",student,q.regionCodes());
        return get(student);
    }
}
