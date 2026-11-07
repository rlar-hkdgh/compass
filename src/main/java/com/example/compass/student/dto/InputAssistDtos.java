package com.example.compass.student.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static com.example.compass.student.dto.StudentDtos.CourseInput;

public final class InputAssistDtos {
    private InputAssistDtos() {}
    public record Issue(String code, String message) {}
    public record RowKey(int sheetIndex, int sourceRow) {}
    public record PreviewRow(int sheetIndex, String sheetName, int sourceRow,
                             Map<String,String> raw, CourseInput course,
                             List<Issue> errors, List<Issue> warnings,
                             List<Long> existingCourseIds) {}
    public record ParsedTranscript(List<PreviewRow> rows, List<Issue> warnings) {}
    public record TranscriptPreview(String previewId, Instant expiresAt,
                                    List<PreviewRow> rows, List<Issue> warnings, BigDecimal gpa, Integer completedSemesters,
                                    WholeImportSummary wholeImport) {}
    public record RowIssue(String sheetName, int sourceRow, List<Issue> issues) {}
    public record WholeImportSummary(int ready, int skipped, List<RowIssue> rowIssues) {}
    public record ConfirmWholeTranscript(@NotBlank String previewId) {}
    public record WholeImportResult(int inserted, int skipped, List<RowIssue> rowIssues, BigDecimal savedGpa, Integer savedCompletedSemesters) {}
    public record ConfirmedRow(@Min(0) int sheetIndex, @Min(1) int sourceRow,
                               @NotNull @Valid CourseInput course) {}
    public record ConfirmTranscript(@NotBlank String previewId,
                                    @NotNull @Size(min=1,max=1000) List<@NotNull @Valid ConfirmedRow> rows, Boolean saveGpa) {}
    public record ImportResult(int inserted, int skipped, List<Long> insertedCourseIds, BigDecimal savedGpa) {}
    public record ReadmeView(long projectId, String repositoryUrl, String readmePath,
                             String readmeSha, String content, Instant lastSuccessAt,
                             Instant lastAttemptAt, String lastAttemptStatus, String lastErrorCode) {}
}
