package com.example.compass.recruit.dto.response;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record CompanyResponse(Long id,String name,String companyInfoUrl) {}
