package com.example.compass.student.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
/** Unrelated CRUD tests start with a verified signup proof; actual mail/confirmation is tested separately. */
final class VerifiedSignupFixture {
 static HttpSession prepare(JdbcTemplate db,HttpSession session,String email){
  db.update("insert into signup_email_challenge(school_email,owner_hash,verified_until) values(?,?,?)",email,SignupEmailService.owner(session),Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")));
  return session;
 }
}
