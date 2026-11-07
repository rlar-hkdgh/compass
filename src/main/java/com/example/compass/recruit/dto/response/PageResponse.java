package com.example.compass.recruit.dto.response;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record PageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
