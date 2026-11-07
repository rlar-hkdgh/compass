package com.example.compass.student.controller;
import com.example.compass.student.service.EmailVerificationService;
import com.example.compass.student.service.AccountWithdrawalService;
import com.example.compass.student.dto.StudentDtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import java.security.Principal;
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {
    public record ConfirmEmail(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Withdrawal(@NotBlank @Size(max=64) String currentPassword) {}
    private final EmailVerificationService emails;
    private final AccountWithdrawalService withdrawal;
    public AccountController(EmailVerificationService emails,AccountWithdrawalService withdrawal) {this.emails=emails;this.withdrawal=withdrawal;}
    @GetMapping("/email-verification")
    public EmailVerificationService.Status status(Principal p) {return emails.status(Long.parseLong(p.getName()));}
    @PostMapping("/email-verification")
    public EmailVerificationService.Status send(Principal p) {return emails.send(Long.parseLong(p.getName()));}
    @PostMapping("/email-verification/confirm")
    public StudentDtos.Profile confirm(Principal p,@Valid @RequestBody ConfirmEmail q) {return emails.confirm(Long.parseLong(p.getName()),q.code());}
    @DeleteMapping
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void withdraw(Principal p,@Valid @RequestBody Withdrawal q,HttpServletRequest request,HttpServletResponse response) {
        withdrawal.withdraw(Long.parseLong(p.getName()),q.currentPassword());
        new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        var cookie=new Cookie("JSESSIONID","");cookie.setMaxAge(0);cookie.setHttpOnly(true);cookie.setSecure(request.isSecure());
        cookie.setPath(request.getContextPath().isEmpty()?"/":request.getContextPath());response.addCookie(cookie);
    }
}
