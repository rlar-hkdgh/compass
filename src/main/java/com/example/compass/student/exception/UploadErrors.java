package com.example.compass.student.exception;


import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Multipart parsing can fail before MVC has selected a controller, so this advice is unscoped. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class UploadErrors {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrors.Error> tooLarge(Exception e) {
        return ResponseEntity.status(413).body(new ApiErrors.Error("UPLOAD_TOO_LARGE","5MiB 이하의 XLSX 파일을 올리세요."));
    }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.multipart.MultipartException.class})
    ResponseEntity<ApiErrors.Error> multipart(Exception e) {
        return ResponseEntity.badRequest().body(new ApiErrors.Error("INVALID_REQUEST","file 필드에 XLSX 파일을 첨부하세요."));
    }
}
