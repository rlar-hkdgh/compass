package com.example.compass.recruit.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record CompanyRequest(@NotBlank @Size(max=255) String name, @Size(max=2000) String companyInfoUrl) {}
