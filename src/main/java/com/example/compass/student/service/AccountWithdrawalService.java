package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
@Service
public class AccountWithdrawalService {
    private final JdbcTemplate db;
    private final StudentInputService students;
    private final PasswordEncoder encoder;
    private final AccountSessions sessions;
    private final TransactionTemplate tx;
    public AccountWithdrawalService(JdbcTemplate db,StudentInputService students,PasswordEncoder encoder,
            AccountSessions sessions,PlatformTransactionManager transactions) {
        this.db=db;this.students=students;this.encoder=encoder;this.sessions=sessions;this.tx=new TransactionTemplate(transactions);
    }
    public void withdraw(long id,String password) {
        tx.executeWithoutResult(t->{
            students.lock(id);
            String hash=db.queryForObject("select password_hash from student where id=?",String.class,id);
            boolean matches=false;
            try { matches=password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72 && encoder.matches(password,hash); }
            catch(IllegalArgumentException ignored) {}
            if(!matches) throw new ApiErrors.Failure(401,"INVALID_CREDENTIALS","현재 비밀번호가 올바르지 않습니다.");
            db.update("delete from recommended_task where student_id=?",id);
            db.update("delete from gap_analysis_category where gap_analysis_id in (select id from gap_analysis where student_id=?)",id);
            db.update("delete from gap_analysis where student_id=?",id);
            db.update("delete from student_project_readme where project_id in (select id from student_project where student_id=?)",id);
            db.update("delete from student_project_tech_stack where project_id in (select id from student_project where student_id=?)",id);
            for(String table:List.of("student_project","student_course","student_internship","student_award",
                    "student_certification","student_major","student_preferred_job_category","student_preferred_region",
                    "student_preferred_employment_type","student_salary_preference","student_career_goal","student_onboarding_step","student_email_verification"))
                db.update("delete from "+table+" where student_id=?",id);
            db.update("delete from signup_email_challenge where school_email=(select school_email from student where id=?)",id);
            db.update("delete from student where id=?",id);
        });
        // Revoke only after successful commit; a foreign-key failure rolls back every deletion.
        sessions.revoke(Long.toString(id));
    }
}
