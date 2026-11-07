package com.example.compass.student.controller;
import com.example.compass.student.dto.OnboardingDtos;
import com.example.compass.student.service.OnboardingService;


import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import static com.example.compass.student.dto.OnboardingDtos.*;

@RestController
@RequestMapping("/api/v1/me/onboarding")
public class OnboardingController {
    private final OnboardingService service;
    public OnboardingController(OnboardingService service) { this.service=service; }
    @GetMapping public View get(Principal principal) { return service.get(Long.parseLong(principal.getName())); }
    @PatchMapping public View patch(Principal principal,@Valid @RequestBody Patch request) {
        return service.patch(Long.parseLong(principal.getName()),request);
    }
}
