package com.example.compass.student.controller;
import com.example.compass.student.service.SignupEmailService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth/signup/email")
public class SignupEmailController {
 public record SchoolEmail(@NotBlank @Email @Size(max=254) @Pattern(regexp="[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)") String schoolEmail){}
 public record ConfirmSignupEmail(@NotBlank @Email @Size(max=254) @Pattern(regexp="[^@\\s]+@(sju\\.ac\\.kr|sejong\\.co\\.kr)") String schoolEmail,@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
 private final SignupEmailService service;
 public SignupEmailController(SignupEmailService service){this.service=service;}
 @PostMapping("/availability") public SignupEmailService.Availability availability(@Valid @RequestBody SchoolEmail q){return service.availability(q.schoolEmail());}
 @PostMapping("/send") public SignupEmailService.Challenge send(@Valid @RequestBody SchoolEmail q,HttpSession session){return service.send(q.schoolEmail(),session);}
 @PostMapping("/confirm") public SignupEmailService.Challenge confirm(@Valid @RequestBody ConfirmSignupEmail q,HttpSession session){return service.confirm(q.schoolEmail(),q.code(),session);}
}
