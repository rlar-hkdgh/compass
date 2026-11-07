package com.example.compass.student.controller;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.service.AccountSessions;
import com.example.compass.student.service.StudentInputService;
import com.example.compass.student.service.SignupEmailService;
import com.example.compass.student.dto.StudentDtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController @RequestMapping("/api/v1") public class AuthController {
    private final JdbcTemplate db;
    private final AccountSessions accountSessions;
    private final SignupEmailService signupEmails;
    private final PasswordEncoder encoder;
    private final org.springframework.security.authentication.AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final SessionRegistry registry;
    private final TransactionTemplate tx;
    private final StudentInputService students;
    public AuthController(JdbcTemplate db,PasswordEncoder encoder,SecurityContextRepository contexts,SessionAuthenticationStrategy sessions,SessionRegistry registry,PlatformTransactionManager transactions,StudentInputService students,org.springframework.security.authentication.AuthenticationManager authentication,AccountSessions accountSessions,SignupEmailService signupEmails){
        this.db=db;
        this.accountSessions=accountSessions;
        this.signupEmails=signupEmails;
        this.encoder=encoder;
        this.contexts=contexts;
        this.sessions=sessions;
        this.registry=registry;
        this.tx=new TransactionTemplate(transactions);
        this.students=students;
        this.authentication=authentication;
    }
    public record Signup(@NotBlank @Size(min=10,max=64) String password,@NotBlank @Email @Size(max=254) @Pattern(regexp="[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)") String schoolEmail,@NotBlank @Size(max=100) String name,@NotNull Boolean isTransfer){
    }
    public record Login(@NotBlank @Email @Size(max=254) @Pattern(regexp="[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)") String schoolEmail,@NotBlank @Size(max=64) String password){
    }
    private void password(String value){
        if(value.getBytes(StandardCharsets.UTF_8).length>72)throw ApiErrors.invalid("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
    }
    private boolean matches(String value,String hash){
        try{
            return encoder.matches(value,hash);
        }
        catch(IllegalArgumentException e){
            return false;
        }
    }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token){
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @PostMapping("/auth/signup") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)  public StudentDtos.Profile signup(@Valid @RequestBody Signup q,HttpSession session){
        password(q.password());
        if(!q.schoolEmail().equals(q.schoolEmail().toLowerCase(Locale.ROOT)))throw ApiErrors.invalid("학교 이메일은 소문자로 입력하세요.");
        return tx.execute(status->{
            if(db.queryForObject("select count(*) from student where school_email=?",Integer.class,q.schoolEmail())>0)
                throw new ApiErrors.Failure(409,"EMAIL_EXISTS","이미 사용 중인 이메일입니다.");
            signupEmails.requireVerified(q.schoolEmail(),session);
            long id=students.insert("student","password_hash,school_email,name,is_transfer",encoder.encode(q.password()),q.schoolEmail(),q.name(),q.isTransfer());
            signupEmails.consume(q.schoolEmail(),id);
            return students.profile(id);
        });
    }
    @PostMapping("/auth/login") public StudentDtos.Profile login(@Valid @RequestBody Login q,HttpServletRequest request,HttpServletResponse response){
        password(q.password());
        if(!q.schoolEmail().equals(q.schoolEmail().toLowerCase(Locale.ROOT)))throw ApiErrors.invalid("학교 이메일은 소문자로 입력하세요.");
        return tx.execute(status->{
            var rows=db.queryForList("select id from student where school_email=? for update",q.schoolEmail());try{
                authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(q.schoolEmail(),q.password()));
            }
            catch(org.springframework.security.core.AuthenticationException | IllegalArgumentException e){
                throw new ApiErrors.Failure(401,"INVALID_CREDENTIALS","학교 이메일 또는 비밀번호가 올바르지 않습니다.");
            }
            if(rows.isEmpty())throw new ApiErrors.Failure(401,"INVALID_CREDENTIALS","학교 이메일 또는 비밀번호가 올바르지 않습니다.");String id=rows.get(0).get("id").toString();var auth=UsernamePasswordAuthenticationToken.authenticated(id,null,List.of());sessions.onAuthentication(auth,request,response);var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);accountSessions.track(request.getSession(false));return students.profile(Long.parseLong(id));
        });
    }
    @PatchMapping("/me/password") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)  public void changePassword(Principal principal,@Valid @RequestBody StudentDtos.PasswordChange q,HttpServletRequest request,HttpServletResponse response){
        password(q.newPassword());
        long id=Long.parseLong(principal.getName());
        tx.executeWithoutResult(status->{
            students.lock(id);String hash=db.queryForObject("select password_hash from student where id=?",String.class,id);if(!matches(q.currentPassword(),hash))throw new ApiErrors.Failure(401,"INVALID_CREDENTIALS","현재 비밀번호가 올바르지 않습니다.");db.update("update student set password_hash=? where id=?",encoder.encode(q.newPassword()),id);registry.getAllSessions(principal.getName(),false).forEach(s->s.expireNow());
        });
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
    }
}
