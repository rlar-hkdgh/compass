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
            return new CareerGoal(r.getString("job_category_code"),sizes,r.getLong("target_salary"),
                students.selected("student_preferred_region","region_code","student_id",student),r.getString("target_company"),
                YearMonth.from(r.getObject("target_date",LocalDate.class)).toString());
        },student).stream().findFirst().orElseThrow(ApiErrors::missing);
    }
    public CareerGoal save(long student,CareerGoalInput q){
        students.lock(student);
        students.code("job_category",q.jobCategoryCode());
        students.codes("region",q.regionCodes());
        var sizes=q.companySizes();
        if(sizes==null || sizes.isEmpty()) throw ApiErrors.invalid("기업 규모를 하나 이상 선택하세요.");
        if(new HashSet<>(sizes).size()!=sizes.size())
            throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","기업 규모를 중복 선택할 수 없습니다.");

        var sizeCodes=sizes.stream().map(CompanySize::name).sorted().toList();
        var date=YearMonth.parse(q.targetDate()).atDay(1);
        int updated=db.update("""
            update student_career_goal set job_category_code=?,target_salary=?,target_company=?,target_date=?
                where student_id=?
            """,q.jobCategoryCode(),q.targetSalary(),q.targetCompany(),date,student);
        if(updated==0){
            db.update("""
                insert into student_career_goal(student_id,job_category_code,target_salary,target_company,target_date)
                values(?,?,?,?,?)
                """,student,q.jobCategoryCode(),q.targetSalary(),q.targetCompany(),date);
        }
        students.replace(COMPANY_SIZES_TABLE,"company_size","student_id",student,sizeCodes);
        students.replace("student_preferred_region","region_code","student_id",student,q.regionCodes());
        return get(student);
    }
}
