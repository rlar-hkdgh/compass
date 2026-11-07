package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class SignupEmailService {
 static final String OWNER=SignupEmailService.class.getName()+".owner";
 private static final String BUDGET=SignupEmailService.class.getName()+".budget";
 public record Availability(String schoolEmail,boolean available){}
 public record Challenge(String schoolEmail,boolean emailVerified,Instant expiresAt,Instant verifiedUntil,Instant resendAvailableAt){}
 private record State(String owner,String hash,Instant expires,Instant verified,Instant sent,Instant window,int count,int failed){}
 private final JdbcTemplate db;private final PasswordEncoder encoder;private final VerificationMailSender mail;private final Clock clock;private final TransactionTemplate tx;
 private final SecureRandom random=new SecureRandom();
 public SignupEmailService(JdbcTemplate db,PasswordEncoder encoder,VerificationMailSender mail,Clock clock,PlatformTransactionManager manager){
  this.db=db;this.encoder=encoder;this.mail=mail;this.clock=clock;this.tx=new TransactionTemplate(manager);
 }
 static String owner(HttpSession session){
  synchronized(session){
   String value=(String)session.getAttribute(OWNER);
   if(value==null){value=UUID.randomUUID().toString();session.setAttribute(OWNER,value);}
   try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
   catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
 }
 private void validate(String email){
  if(email==null || !email.equals(email.toLowerCase(Locale.ROOT)) || email.length()>254 || !email.matches("[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)"))throw ApiErrors.invalid("소문자 학교 이메일을 입력하세요.");
 }
 public Availability availability(String email){
  validate(email);return new Availability(email,db.queryForObject("select count(*) from student where school_email=?",Integer.class,email)==0);
 }
 private void available(String email){if(!availability(email).available())throw new ApiErrors.Failure(409,"EMAIL_EXISTS","이미 가입된 학교 이메일입니다.");}
 private static Instant time(java.sql.ResultSet r,String field)throws java.sql.SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
 private State state(String email){
  return db.query("select * from signup_email_challenge where school_email=? for update",(r,n)->new State(r.getString("owner_hash"),r.getString("code_hash"),time(r,"expires_at"),time(r,"verified_until"),time(r,"last_sent_at"),time(r,"window_started_at"),r.getInt("send_count"),r.getInt("failed_attempts")),email)
   .stream().findFirst().orElse(null);
 }
 private void budget(HttpSession session){
  synchronized(session){
   var now=clock.instant();
   var b=(long[])session.getAttribute(BUDGET);
   if(b==null || now.getEpochSecond()-b[0]>=86400)b=new long[]{now.getEpochSecond(),0};
   if(b[1]>=10)throw new ApiErrors.Failure(429,"EMAIL_SEND_LIMIT","세션당 24시간 최대 10회 발송 가능합니다.");
   b[1]++;session.setAttribute(BUDGET,b);
  }
 }
 public Challenge send(String email,HttpSession session){
  available(email);String owner=owner(session);budget(session);
  boolean ok=Boolean.TRUE.equals(tx.execute(t->{
   // ON CONFLICT DO NOTHING is supported by PostgreSQL and H2 PostgreSQL mode.
   db.update("insert into signup_email_challenge(school_email) values(?) on conflict do nothing",email);
   var s=state(email);available(email);var now=clock.instant();
   if(s.sent()!=null && now.isBefore(s.sent().plusSeconds(60)))throw new ApiErrors.Failure(429,"EMAIL_RESEND_COOLDOWN","60초 후 다시 요청하세요.");
   boolean same=s.window()!=null && now.isBefore(s.window().plusSeconds(86400));
   if(same && s.count()>=10)throw new ApiErrors.Failure(429,"EMAIL_SEND_LIMIT","이메일당 24시간 최대 10회 발송 가능합니다.");
   String code;
   do{code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));}while(s.hash()!=null && encoder.matches(code,s.hash()));
   db.update("update signup_email_challenge set owner_hash=?,code_hash=?,expires_at=?,verified_until=NULL,last_sent_at=?,window_started_at=?,send_count=?,failed_attempts=0 where school_email=?",
    owner,encoder.encode(code),Timestamp.from(now.plusSeconds(600)),Timestamp.from(now),Timestamp.from(same?s.window():now),same?s.count()+1:1,email);
   try{mail.send(email,code);return true;}catch(RuntimeException e){
    db.update("update signup_email_challenge set code_hash=NULL,expires_at=NULL,verified_until=NULL where school_email=?",email);return false;
   }
  }));
  if(!ok)throw new ApiErrors.Failure(503,"EMAIL_DELIVERY_FAILED","인증 메일을 발송하지 못했습니다.");
  return new Challenge(email,false,clock.instant().plusSeconds(600),null,clock.instant().plusSeconds(60));
 }
 public Challenge confirm(String email,String code,HttpSession session){
  validate(email);String owner=owner(session);
  String error=tx.execute(t->{
   var s=state(email);available(email);var now=clock.instant();
   if(s==null || !owner.equals(s.owner()) || s.hash()==null)return "EMAIL_CODE_INVALID";
   if(!now.isBefore(s.expires())){
    db.update("update signup_email_challenge set code_hash=NULL,expires_at=NULL where school_email=?",email);return "EMAIL_CODE_EXPIRED";
   }
   if(s.failed()>=5)return "EMAIL_ATTEMPTS_EXCEEDED";
   if(!encoder.matches(code,s.hash())){
    db.update("update signup_email_challenge set failed_attempts=failed_attempts+1 where school_email=?",email);
    return s.failed()+1>=5?"EMAIL_ATTEMPTS_EXCEEDED":"EMAIL_CODE_INVALID";
   }
   db.update("update signup_email_challenge set code_hash=NULL,expires_at=NULL,verified_until=? where school_email=?",Timestamp.from(now.plusSeconds(600)),email);
   return null;
  });
  if(error!=null)throw new ApiErrors.Failure(error.equals("EMAIL_ATTEMPTS_EXCEEDED")?429:400,error,"인증번호와 세션을 확인하세요.");
  return new Challenge(email,true,null,clock.instant().plusSeconds(600),null);
 }
 /** Must run within signup's transaction. Lock stays held until student and verified state commit together. */
 public void requireVerified(String email,HttpSession session){
  var s=state(email);
  if(s==null || !owner(session).equals(s.owner()) || s.verified()==null || !clock.instant().isBefore(s.verified()))
   throw new ApiErrors.Failure(400,"EMAIL_VERIFICATION_REQUIRED","같은 세션에서 학교 이메일 인증을 완료한 후 10분 안에 가입하세요.");
 }
 public void consume(String email,long student){
  var now=Timestamp.from(clock.instant());
  db.update("insert into student_email_verification(student_id,school_email,verified_at,last_sent_at,window_started_at,send_count,failed_attempts) values(?,?,?,?,?,1,0)",student,email,now,now,now);
  // Retain the email send quota, but erase the proof and session binding on successful signup.
  db.update("update signup_email_challenge set owner_hash=NULL,code_hash=NULL,expires_at=NULL,verified_until=NULL where school_email=?",email);
 }
}
