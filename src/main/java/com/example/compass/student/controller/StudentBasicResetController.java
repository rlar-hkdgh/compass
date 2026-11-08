package com.example.compass.student.controller;

import com.example.compass.student.service.StudentInputService;
import com.example.compass.student.service.TranscriptImportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@ConditionalOnProperty(name = "compass.student.basic-reset-enabled", havingValue = "true")
public class StudentBasicResetController {
    private final StudentInputService students;
    private final TranscriptImportService transcripts;

    public StudentBasicResetController(StudentInputService students, TranscriptImportService transcripts) {
        this.students = students;
        this.transcripts = transcripts;
    }

    @DeleteMapping("/api/v1/me/basic-info")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(Principal principal, HttpSession session) {
        transcripts.discardPreview(session);
        students.resetBasicInfo(Long.parseLong(principal.getName()));
    }
}
