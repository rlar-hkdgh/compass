package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Service
public class EmailVerificationService {
    public record Status(String schoolEmail,boolean emailVerified,Instant expiresAt,Instant resendAvailableAt) {}
    private record State(String email,Instant verified,String hash,Instant expires,Instant sent,Instant window,int count,int failed) {}
    private final JdbcTemplate db;
    private final StudentInputService students;
    private final PasswordEncoder encoder;
    private final VerificationMailSender mail;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();
    public EmailVerificationService(JdbcTemplate db,StudentInputService students,PasswordEncoder encoder,
            VerificationMailSender mail,Clock clock,PlatformTransactionManager transactions) {
        this.db=db;this.students=students;this.encoder=encoder;this.mail=mail;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    private static Instant instant(java.sql.ResultSet r,String name) throws java.sql.SQLException {
        var value=r.getTimestamp(name);return value==null?null:value.toInstant();
    }
    private State state(long id) {
        return db.query("select * from student_email_verification where student_id=?",(r,n)->new State(
            r.getString("school_email"),instant(r,"verified_at"),r.getString("code_hash"),instant(r,"expires_at"),
            instant(r,"last_sent_at"),instant(r,"window_started_at"),r.getInt("send_count"),r.getInt("failed_attempts")),id)
            .stream().findFirst().orElse(null);
    }
    public Status status(long id) {
        var profile=students.profile(id);var s=state(id);
        Instant retry=s==null?null:(s.count()>=10?s.window().plus(Duration.ofHours(24)):s.sent().plusSeconds(60));
        return new Status(profile.schoolEmail(),profile.emailVerified(),s==null?null:s.expires(),retry);
    }
    public Status send(long id) {
        // Serialize with withdrawal and other student writes. SMTP uses bounded timeouts.
        // Delivery failures commit quota consumption and invalidate the previous code.
        boolean delivered=Boolean.TRUE.equals(tx.execute(t->{
            students.lock(id);
            var profile=students.profile(id);
            if(profile.emailVerified()) throw new ApiErrors.Failure(409,"EMAIL_ALREADY_VERIFIED","이미 인증된 학교 이메일입니다.");
            if(!profile.schoolEmail().matches("[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)"))
                throw ApiErrors.invalid("등록된 학교 이메일 도메인을 확인하세요.");
            var s=state(id);Instant now=clock.instant();
            if(s!=null && now.isBefore(s.sent().plusSeconds(60)))
                throw new ApiErrors.Failure(429,"EMAIL_RESEND_COOLDOWN","60초 후 다시 요청하세요.");
            boolean sameWindow=s!=null && now.isBefore(s.window().plus(Duration.ofHours(24)));
            if(sameWindow && s.count()>=10) throw new ApiErrors.Failure(429,"EMAIL_SEND_LIMIT","24시간 동안 최대 10회 발송할 수 있습니다.");
            String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
            db.update("delete from student_email_verification where student_id=?",id);
            db.update("""
                insert into student_email_verification(student_id,school_email,code_hash,expires_at,last_sent_at,window_started_at,send_count,failed_attempts)
                values(?,?,?,?,?,?,?,0)
                """,id,profile.schoolEmail(),encoder.encode(code),Timestamp.from(now.plusSeconds(600)),Timestamp.from(now),
                Timestamp.from(sameWindow?s.window():now),sameWindow?s.count()+1:1);
            try { mail.send(profile.schoolEmail(),code);return true; }
            catch(RuntimeException e) {
                // SMTP exceptions can contain credentials; never log or return their text.
                db.update("update student_email_verification set code_hash=NULL,expires_at=NULL where student_id=?",id);
                return false;
            }
        }));
        if(!delivered) throw new ApiErrors.Failure(503,"EMAIL_DELIVERY_FAILED","인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도하세요.");
        return status(id);
    }
    public StudentDtos.Profile confirm(long id,String code) {
        // Throw outside the transaction so failed-attempt counts are committed.
        String error=tx.execute(t->{
            students.lock(id);var profile=students.profile(id);var s=state(id);
            if(profile.emailVerified()) return "EMAIL_ALREADY_VERIFIED";
            if(s==null || s.hash()==null || !s.email().equals(profile.schoolEmail())) return "EMAIL_CODE_INVALID";
            if(!clock.instant().isBefore(s.expires())) {
                db.update("update student_email_verification set code_hash=NULL,expires_at=NULL where student_id=?",id);
                return "EMAIL_CODE_EXPIRED";
            }
            if(s.failed()>=5) return "EMAIL_ATTEMPTS_EXCEEDED";
            if(!encoder.matches(code,s.hash())) {
                db.update("update student_email_verification set failed_attempts=failed_attempts+1 where student_id=?",id);
                return s.failed()+1>=5?"EMAIL_ATTEMPTS_EXCEEDED":"EMAIL_CODE_INVALID";
            }
            db.update("update student_email_verification set verified_at=?,code_hash=NULL,expires_at=NULL where student_id=?",Timestamp.from(clock.instant()),id);
            return null;
        });
        if(error!=null) throw new ApiErrors.Failure(error.equals("EMAIL_ALREADY_VERIFIED")?409:error.equals("EMAIL_ATTEMPTS_EXCEEDED")?429:400,error,"인증 상태와 번호를 확인하고 필요하면 다시 발송하세요.");
        return students.profile(id);
    }
}
