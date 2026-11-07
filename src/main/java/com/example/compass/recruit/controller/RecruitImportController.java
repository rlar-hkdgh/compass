package com.example.compass.recruit.controller;
import com.example.compass.recruit.service.RecruitService;
import com.example.compass.recruit.dto.request.CompanyRequest;
import com.example.compass.recruit.dto.response.CompanyResponse;
import com.example.compass.recruit.dto.request.PostingRequest;
import com.example.compass.recruit.dto.response.PostingDetail;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
/** 로컬 검증 전용. 운영 환경에서는 등록되지 않는다. 수집기는 RecruitService를 직접 호출한다. */
@RestController
@RequestMapping("/internal/recruit")
@ConditionalOnProperty(name="compass.recruit.import-enabled",havingValue="true")
public class RecruitImportController {
    private final RecruitService service;
    public RecruitImportController(RecruitService service) { this.service=service; }
    @PostMapping("/companies") @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse company(@Valid @RequestBody CompanyRequest request) { return service.createCompany(request); }
    @PutMapping("/job-postings/{source}/{sourcePostingId}")
    public PostingDetail save(@PathVariable String source,@PathVariable String sourcePostingId,
                              @Valid @RequestBody PostingRequest request) {
        return service.save(source,sourcePostingId,request);
    }
}
