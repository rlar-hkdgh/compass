package com.example.compass.recruit.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record CodeSelection(
        @Size(max=100) List<@NotBlank @Size(max=32) String> regions,
        @Size(max=100) List<@NotBlank @Size(max=32) String> employmentTypes, @Size(max=100) List<@NotBlank @Size(max=32) String> jobCategories,
        @Size(max=32) String educationLevel, @Size(max=32) String deadlineType,
        @Size(max=32) String salaryUnit, @Size(max=32) String salaryDecision, @Size(max=32) String experienceType,
        @Size(max=250) List<@NotBlank @Size(max=32) String> specialties,
        @Size(max=250) List<@NotBlank @Size(max=32) String> techStacks) {}
