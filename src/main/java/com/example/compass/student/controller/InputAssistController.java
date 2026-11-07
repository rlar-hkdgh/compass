package com.example.compass.student.controller;
import com.example.compass.student.dto.InputAssistDtos;
import com.example.compass.student.service.TranscriptImportService;
import com.example.compass.student.service.ProjectReadmeService;


import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import static com.example.compass.student.dto.InputAssistDtos.*;

@RestController @RequestMapping("/api/v1/me")
public class InputAssistController {
    private final TranscriptImportService transcripts;
    private final ProjectReadmeService readmes;
    public InputAssistController(TranscriptImportService transcripts,ProjectReadmeService readmes) { this.transcripts=transcripts;this.readmes=readmes; }
    @PostMapping(value="/courses/import/preview",consumes="multipart/form-data")
    public TranscriptPreview preview(Principal principal,HttpSession session,@RequestPart("file") MultipartFile file) {
        return transcripts.preview(Long.parseLong(principal.getName()),session,file);
    }
    @PostMapping("/courses/import/confirm")
    public ImportResult confirm(Principal principal,HttpSession session,@Valid @RequestBody ConfirmTranscript request) {
        return transcripts.confirm(Long.parseLong(principal.getName()),session,request);
    }
    @PostMapping("/courses/import/confirm-all")
    public WholeImportResult confirmAll(Principal principal, HttpSession session, @Valid @RequestBody ConfirmWholeTranscript request) {
        return transcripts.confirmAll(Long.parseLong(principal.getName()), session, request);
    }
    @GetMapping("/projects/{id}/readme")
    public ReadmeView readme(Principal principal,@PathVariable long id) { return readmes.get(Long.parseLong(principal.getName()),id); }
    @PostMapping("/projects/{id}/readme/collect")
    public ReadmeView collect(Principal principal,@PathVariable long id) { return readmes.collect(Long.parseLong(principal.getName()),id); }
}
