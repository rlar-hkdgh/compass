package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.compass.student.dto.StudentDtos.*;
@Service @Transactional public class StudentInputService {
    private final JdbcTemplate db;
    public StudentInputService(JdbcTemplate db){
        this.db=db;
    }
    // Serializes replacement operations for the same student, including multi-table preferences.
    public void lock(long student){
        if(db.queryForList("select id from student where id=? for update",student).isEmpty())throw ApiErrors.missing();
    }
    @Transactional(readOnly=true) public Profile profile(long student){
        return db.query("""
            select s.id,s.school_email,s.name,s.is_transfer,
                (v.verified_at is not null and v.school_email=s.school_email) as email_verified,
                s.semester,s.gpa,s.gpa_scale
            from student s left join student_email_verification v on v.student_id=s.id where s.id=?
            """,(r,n)->new Profile(r.getLong(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getBoolean(5),
                r.getObject(6,Integer.class),r.getBigDecimal(7),r.getBigDecimal(8)),student)
            .stream().findFirst().orElseThrow(ApiErrors::missing);
    }
    public Profile patch(long student,ProfilePatch q){
        lock(student);
        Profile current=profile(student);
        var gpa=q.gpa()==null?current.gpa():q.gpa();
        var scale=q.gpaScale()==null?current.gpaScale():q.gpaScale();
        if((gpa==null)!=(scale==null) || (gpa!=null && gpa.compareTo(scale)>0))
            throw ApiErrors.invalid("평점과 만점은 함께 설정해야 하며 평점은 만점 이하여야 합니다.");
        if(q.name()!=null && q.name().isBlank())throw ApiErrors.invalid("이름은 공백일 수 없습니다.");
        db.update("""
            update student set name=?,is_transfer=?,semester=?,gpa=?,gpa_scale=? where id=?
            """,q.name()==null?current.name():q.name(),q.isTransfer()==null?current.isTransfer():q.isTransfer(),
            q.semester()==null?current.semester():q.semester(),gpa,scale,student);
        return profile(student);
    }

    public void resetBasicInfo(long student) {
        lock(student);
        db.update("delete from student_major where student_id=?", student);
        db.update("delete from student_course where student_id=?", student);
        db.update("update student set semester=null, gpa=null, gpa_scale=null where id=?", student);
        db.update("""
            delete from student_onboarding_step where student_id=?
                and step_code in ('BASIC_INFO', 'MAJORS', 'COURSES')
            """, student);
    }

    void code(String table,String value){
        if(value==null||value.isBlank()||value.length()>32||db.queryForObject("select count(*) from "+table+" where code=?",Integer.class,value)!=1)throw new ApiErrors.Failure(400,"UNKNOWN_CODE","등록되지 않은 코드입니다.");
    }
    void codes(String table,List<String> values){
        if(values==null||values.size()>100)throw ApiErrors.invalid("코드 배열이 필요합니다.");
        if(new HashSet<>(values).size()!=values.size())throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","중복 코드는 허용하지 않습니다.");
        values.forEach(v->code(table,v));
    }
    List<String> selected(String table,String column,String owner,long id){
        return db.queryForList("select "+column+" from "+table+" where "+owner+"=? order by "+column,String.class,id);
    }
    void replace(String table,String column,String owner,long id,List<String> values){
        db.update("delete from "+table+" where "+owner+"=?",id);
        for(String value:values)db.update("insert into "+table+"("+owner+","+column+") values(?,?)",id,value);
    }
    @Transactional(readOnly=true) public Majors majors(long student){
        return new Majors(db.query("select major_code,completion_type_code from student_major where student_id=? order by major_code,completion_type_code",(r,n)->new MajorSelection(r.getString(1),r.getString(2)),student));
    }
    public Majors majors(long student,Majors q){
        lock(student);
        if(new HashSet<>(q.majors()).size()!=q.majors().size())throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","전공 선택이 중복되었습니다.");
        for(var m:q.majors()){
            code("major",m.majorCode());
            code("major_completion_type",m.completionTypeCode());
        }
        db.update("delete from student_major where student_id=?",student);
        for(var m:q.majors())db.update("insert into student_major(student_id,major_code,completion_type_code) values(?,?,?)",student,m.majorCode(),m.completionTypeCode());
        return majors(student);
    }
    @Transactional(readOnly=true) public Preferences preferences(long student){
        var salary=db.queryForList("select min_amount,salary_unit_code from student_salary_preference where student_id=?",student);
        return new Preferences(selected("student_preferred_job_category","job_category_code","student_id",student),selected("student_preferred_region","region_code","student_id",student),selected("student_preferred_employment_type","employment_type_code","student_id",student),salary.isEmpty()?null:((Number)salary.get(0).get("min_amount")).longValue(),salary.isEmpty()?null:(String)salary.get(0).get("salary_unit_code"));
    }
    public Preferences preferences(long student,Preferences q){
        lock(student);
        codes("job_category",q.jobCategoryCodes());
        codes("region",q.regionCodes());
        codes("employment_type",q.employmentTypeCodes());
        if((q.minAmount()==null)!=(q.salaryUnitCode()==null))throw ApiErrors.invalid("급여 금액과 단위는 함께 지정하거나 함께 해제하세요.");
        if(q.salaryUnitCode()!=null)code("salary_unit",q.salaryUnitCode());
        replace("student_preferred_job_category","job_category_code","student_id",student,q.jobCategoryCodes());
        replace("student_preferred_region","region_code","student_id",student,q.regionCodes());
        replace("student_preferred_employment_type","employment_type_code","student_id",student,q.employmentTypeCodes());
        db.update("delete from student_salary_preference where student_id=?",student);
        if(q.minAmount()!=null)db.update("insert into student_salary_preference(student_id,salary_unit_code,min_amount) values(?,?,?)",student,q.salaryUnitCode(),q.minAmount());
        return preferences(student);
    }
    @Transactional(readOnly=true) public Certifications certifications(long student){
        return new Certifications(selected("student_certification","certification_code","student_id",student));
    }
    public Certifications certifications(long student,Certifications q){
        lock(student);
        codes("certification",q.certificationCodes());
        replace("student_certification","certification_code","student_id",student,q.certificationCodes());
        return certifications(student);
    }
    @Transactional(readOnly=true) public List<Code> codes(String type){
        String table=switch(type){
            case "majors"->"major";
            case "major-completion-types"->"major_completion_type";
            case "certifications"->"certification";
            case "tech-stacks"->"tech_stack";
            case "job-categories"->"job_category";
            case "regions"->"region";
            case "employment-types"->"employment_type";
            case "salary-units"->"salary_unit";
            default->throw new ApiErrors.Failure(404,"CODE_TYPE_NOT_FOUND","코드 종류가 없습니다.");
        };
        return db.query("select code,name from "+table+" order by code",(r,n)->new Code(r.getString(1),r.getString(2)));
    }
    @Transactional(readOnly=true)
    public List<RegionGroup> groupedRegions() {
        var regions = codes("regions");
        Map<String, String> names = new HashMap<>();
        for (var region : regions) names.put(region.code(), region.name());

        Map<String, List<Code>> groups = new LinkedHashMap<>();
        for (var region : regions) {
            String code = region.code();
            String group = code;
            if (code.matches("1[0-9]{5}")) {
                String parent = code.substring(0, 3) + "000";
                if (names.containsKey(parent)) group = parent;
            } else if (code.matches("2[0-9]{5}")) {
                group = "overseas";
            }
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(region);
        }
        return groups.entrySet().stream().map(entry -> new RegionGroup(
            names.getOrDefault(entry.getKey(), "해외").replaceFirst("전체$", ""), List.copyOf(entry.getValue())
        )).toList();
    }
    void owned(String table,long student,long id){
        if(db.queryForObject("select count(*) from "+table+" where student_id=? and id=?",Integer.class,student,id)!=1)throw ApiErrors.missing();
    }
    public long insert(String table,String columns,Object... values){
        String placeholders=String.join(",",Collections.nCopies(values.length,"?"));
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(c->{
            var st=c.prepareStatement("insert into "+table+"("+columns+") values("+placeholders+")",new String[]{
                "id"
            });for(int i=0;i<values.length;i++){
                Object v=values[i];if(v instanceof java.time.LocalDate date)v=java.sql.Date.valueOf(date);st.setObject(i+1,v);
            }
            return st;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    void github(String url) {
        if(url!=null && !url.isBlank()) GitHubRepository.parse(url);
    }
    @Transactional(readOnly=true) public List<Project> projects(long student){
        return db.query("select id,name,overview,description,outcome,github_url from student_project where student_id=? order by id",(r,n)->new Project(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),selected("student_project_tech_stack","tech_stack_code","project_id",r.getLong(1))),student);
    }
    public Project project(long student,long id){
        owned("student_project",student,id);
        return projects(student).stream().filter(p->p.id()==id).findFirst().orElseThrow(ApiErrors::missing);
    }
    public Project saveProject(long student,Long id,ProjectInput q){
        lock(student);
        if(id!=null)owned("student_project",student,id);
        codes("tech_stack",q.techStackCodes());
        github(q.githubUrl());
        String url=q.githubUrl()==null||q.githubUrl().isBlank()?null:q.githubUrl();
        if(id==null)id=insert("student_project","student_id,name,overview,description,outcome,github_url",student,q.name(),q.overview(),q.description(),q.outcome(),url);
        else{
            String previous=db.queryForObject("select github_url from student_project where id=?",String.class,id);
            if(!Objects.equals(previous,url))db.update("delete from student_project_readme where project_id=?",id);
            db.update("update student_project set name=?,overview=?,description=?,outcome=?,github_url=? where id=? and student_id=?",q.name(),q.overview(),q.description(),q.outcome(),url,id,student);
        }
        replace("student_project_tech_stack","tech_stack_code","project_id",id,q.techStackCodes());
        return project(student,id);
    }
    public void deleteProject(long student,long id){
        lock(student);
        owned("student_project",student,id);
        db.update("delete from student_project_readme where project_id=?",id);
        db.update("delete from student_project_tech_stack where project_id=?",id);
        db.update("delete from student_project where id=? and student_id=?",id,student);
    }
    @Transactional(readOnly=true) public List<Internship> internships(long student){
        return db.query("select id,company_name,job_category_code,started_on,ended_on,description from student_internship where student_id=? order by id",(r,n)->new Internship(r.getLong(1),r.getString(2),r.getString(3),r.getDate(4).toLocalDate(),r.getDate(5).toLocalDate(),r.getString(6)),student);
    }
    public Internship internship(long student,long id){
        owned("student_internship",student,id);
        return internships(student).stream().filter(p->p.id()==id).findFirst().orElseThrow(ApiErrors::missing);
    }
    public Internship saveInternship(long student,Long id,InternshipInput q){
        lock(student);
        if(id!=null)owned("student_internship",student,id);
        code("job_category",q.jobCategoryCode());
        if(q.startedOn().isAfter(q.endedOn()))throw ApiErrors.invalid("시작일은 종료일보다 늦을 수 없습니다.");
        if(id==null)id=insert("student_internship","student_id,company_name,job_category_code,started_on,ended_on,description",student,q.companyName(),q.jobCategoryCode(),q.startedOn(),q.endedOn(),q.description());
        else db.update("update student_internship set company_name=?,job_category_code=?,started_on=?,ended_on=?,description=? where id=? and student_id=?",q.companyName(),q.jobCategoryCode(),q.startedOn(),q.endedOn(),q.description(),id,student);
        return internship(student,id);
    }
    @Transactional(readOnly=true) public List<Award> awards(long student){
        return db.query("select id,name,organizer,description from student_award where student_id=? order by id",(r,n)->new Award(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),student);
    }
    public Award award(long student,long id){
        owned("student_award",student,id);
        return awards(student).stream().filter(p->p.id()==id).findFirst().orElseThrow(ApiErrors::missing);
    }
    public Award saveAward(long student,Long id,AwardInput q){
        lock(student);
        if(id!=null)owned("student_award",student,id);
        if(id==null)id=insert("student_award","student_id,name,organizer,description",student,q.name(),q.organizer(),q.description());
        else db.update("update student_award set name=?,organizer=?,description=? where id=? and student_id=?",q.name(),q.organizer(),q.description(),id,student);
        return award(student,id);
    }
    @Transactional(readOnly=true) public List<Course> courses(long student){
        return db.query("select id,course_number,course_name,academic_year,semester,evaluation_method,grade,grade_point from student_course where student_id=? order by id",(r,n)->new Course(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4),r.getString(5),r.getString(6),r.getString(7),r.getBigDecimal(8)),student);
    }
    public Course course(long student,long id){
        owned("student_course",student,id);
        return courses(student).stream().filter(p->p.id()==id).findFirst().orElseThrow(ApiErrors::missing);
    }
    public Course saveCourse(long student,Long id,CourseInput q){
        lock(student);
        if(id!=null)owned("student_course",student,id);
        validateCourse(q);
        if(id==null)id=insert("student_course","student_id,course_number,course_name,academic_year,semester,evaluation_method,grade,grade_point",student,q.courseNumber(),q.courseName(),q.academicYear(),q.semester(),q.evaluationMethod(),q.grade(),q.gradePoint());
        else db.update("update student_course set course_number=?,course_name=?,academic_year=?,semester=?,evaluation_method=?,grade=?,grade_point=? where id=? and student_id=?",q.courseNumber(),q.courseName(),q.academicYear(),q.semester(),q.evaluationMethod(),q.grade(),q.gradePoint(),id,student);
        return course(student,id);
    }
    private static final Map<String,String> GRADE_POINTS=Map.of("A+","4.5","A0","4.0","B+","3.5","B0","3.0","C+","2.5","C0","2.0","D+","1.5","D0","1.0","F","0.0","FA","0.0");

    static java.math.BigDecimal gradePoint(String grade){
        String point=GRADE_POINTS.get(grade);
        return point==null?null:new java.math.BigDecimal(point);
    }

    static void validateCourse(CourseInput q) {
        if("P/NP".equals(q.evaluationMethod())){
            if(!Set.of("P","NP").contains(q.grade())||q.gradePoint()!=null)throw ApiErrors.invalid("P/NP는 P 또는 NP 및 NULL 평점을 사용하세요.");
        }
        else{
            // Sejong official grade mapping: https://www.sejong.ac.kr/kor/academics/grades.do
            var point=gradePoint(q.grade());
            if(point==null||(q.gradePoint()!=null&&q.gradePoint().compareTo(point)!=0))throw ApiErrors.invalid("등급과 평점을 확인하세요.");
        }
    }
    public void deleteInternship(long student,long id){
        delete("student_internship",student,id);
    }
    public void deleteAward(long student,long id){
        delete("student_award",student,id);
    }
    public void deleteCourse(long student,long id){
        delete("student_course",student,id);
    }
    private void delete(String table,long student,long id){
        lock(student);
        owned(table,student,id);
        db.update("delete from "+table+" where id=? and student_id=?",id,student);
    }
}



