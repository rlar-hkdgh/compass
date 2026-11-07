package com.example.compass.recruit.exception;


import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class RecruitExceptionHandler {
    public record ApiError(String code,String message) {}
    @ExceptionHandler(RecruitException.class)
    public ResponseEntity<ApiError> recruit(RecruitException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(),e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST","요청 필드와 형식을 확인하세요."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new ApiError("DATA_CONFLICT","저장된 데이터 또는 참조 관계와 충돌합니다."));
    }
}
