package com.example.compass.recruit.controller;
import com.example.compass.recruit.service.RecruitService;
import com.example.compass.recruit.dto.response.CodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController
@RequestMapping("/api/v1")
public class RecruitController {
    private final RecruitService service;
    public RecruitController(RecruitService service) { this.service=service; }

    @GetMapping("/recruit-codes/{type}")
    public List<CodeResponse> codes(@PathVariable String type) { return service.codes(type); }
}
