package com.example.compass.recruit.dto.response;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record PostingDetail(PostingSummary posting,String sourceUrl,
        Instant applicationStartAt,String keywords,String description,
        CodeResponse deadlineType) {}
