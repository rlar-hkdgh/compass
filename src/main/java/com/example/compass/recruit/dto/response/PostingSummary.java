package com.example.compass.recruit.dto.response;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record PostingSummary(String sourceCode,String sourcePostingId,String title,
        CompanyResponse company,boolean active,List<CodeResponse> jobCategories,List<CodeResponse> regions,
        List<CodeResponse> employmentTypes,CodeResponse educationLevel,String educationNote,
        CodeResponse salaryUnit,CodeResponse salaryDecision,Long salaryMinAmount,Long salaryMaxAmount,
        CodeResponse experienceType,Instant applicationEndAt,
        List<CodeResponse> specialties,List<CodeResponse> techStacks) {}
