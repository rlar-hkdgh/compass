package com.example.compass.student.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(basePackages="com.example.compass.student.controller") public class ApiErrors {
    public record Error(String code,String message) {
    }
    public static class Failure extends RuntimeException {
        public final int status;
        public final String code;
        public Failure(int status,String code,String message){
            super(message);
            this.status=status;
            this.code=code;
        }
    }
    public static Failure invalid(String message){
        return new Failure(400,"INVALID_REQUEST",message);
    }
    public static Failure missing(){
        return new Failure(404,"ITEM_NOT_FOUND","항목을 찾을 수 없습니다.");
    }
    @ExceptionHandler(Failure.class) ResponseEntity<Error> failure(Failure e){
        return ResponseEntity.status(e.status).body(new Error(e.code,e.getMessage()));
    }
    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })  ResponseEntity<Error> invalid(Exception e){
        return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST","요청 필드와 형식을 확인하세요."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)  ResponseEntity<Error> conflict(Exception e){
        return ResponseEntity.status(409).body(new Error("DATA_CONFLICT","중복 또는 참조 관계와 충돌합니다."));
    }
}


