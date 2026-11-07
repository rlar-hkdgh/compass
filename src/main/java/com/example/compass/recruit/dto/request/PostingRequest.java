package com.example.compass.recruit.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record PostingRequest(
        @NotNull @Positive Long companyId,
        @NotBlank @Size(max=500) String title,
        @NotBlank @Size(max=2000) String sourceUrl,
        @NotNull Boolean active,
        Instant applicationStartAt, Instant applicationEndAt,
        @Size(max=20000) String keywords, @Size(max=2000000) String description,
        @Size(max=20000) String educationNote,
        @PositiveOrZero Long salaryMinAmount, @PositiveOrZero Long salaryMaxAmount,
        @NotNull @jakarta.validation.Valid CodeSelection codes) {}
