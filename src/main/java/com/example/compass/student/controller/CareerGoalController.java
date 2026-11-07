package com.example.compass.student.controller;

import com.example.compass.student.dto.CareerGoalDtos.CareerGoal;
import com.example.compass.student.dto.CareerGoalDtos.CareerGoalInput;
import com.example.compass.student.service.CareerGoalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/me/career-goal")
public class CareerGoalController {
    private final CareerGoalService service;
    public CareerGoalController(CareerGoalService service){this.service=service;}
    @GetMapping public CareerGoal get(Principal principal){return service.get(Long.parseLong(principal.getName()));}
    @PutMapping public CareerGoal save(Principal principal,@Valid @RequestBody CareerGoalInput request){
        return service.save(Long.parseLong(principal.getName()),request);
    }
}
