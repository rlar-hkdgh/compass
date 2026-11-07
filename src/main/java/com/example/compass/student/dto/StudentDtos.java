package com.example.compass.student.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public final class StudentDtos {
    private StudentDtos() {
    }
    public record Profile(long id,String schoolEmail,String name,boolean isTransfer,boolean emailVerified,
                          Integer semester,BigDecimal gpa,BigDecimal gpaScale) {
    }
    public record ProfilePatch(@Size(max=100) String name,Boolean isTransfer,
        @PositiveOrZero Integer semester,
        @DecimalMin("0.0") @Digits(integer=1,fraction=2) BigDecimal gpa,
        @DecimalMin(value="0.0",inclusive=false) @DecimalMax("4.5") @Digits(integer=1,fraction=2) BigDecimal gpaScale) {
    }
    public record PasswordChange(@NotBlank @Size(max=64) String currentPassword,@NotBlank @Size(min=10,max=64) String newPassword) {
    }
    public record MajorSelection(@NotBlank @Size(max=32) String majorCode,@NotBlank @Size(max=32) String completionTypeCode) {
    }
    public record Majors(@NotNull @Size(max=100) List<@NotNull @jakarta.validation.Valid MajorSelection> majors) {
    }
    public record Preferences(@NotNull @Size(max=100) List<@NotBlank String> jobCategoryCodes,@NotNull @Size(max=100) List<@NotBlank String> regionCodes,@NotNull @Size(max=100) List<@NotBlank String> employmentTypeCodes,@PositiveOrZero Long minAmount,@Size(max=32) String salaryUnitCode) {
    }
    public record Certifications(@NotNull @Size(max=100) List<@NotBlank String> certificationCodes) {
    }
    public record ProjectInput(@NotBlank @Size(max=255) String name,@NotBlank @Size(max=50000) String overview,@NotBlank @Size(max=50000) String description,@Size(max=50000) String outcome,@Size(max=2000) String githubUrl,@NotNull @Size(max=100) List<@NotBlank String> techStackCodes) {
    }
    public record Project(long id,String name,String overview,String description,String outcome,String githubUrl,List<String> techStackCodes) {
    }
    public record InternshipInput(@NotBlank @Size(max=255) String companyName,@NotBlank @Size(max=32) String jobCategoryCode,@NotNull @PastOrPresent LocalDate startedOn,@NotNull @PastOrPresent LocalDate endedOn,@NotBlank @Size(max=50000) String description) {
    }
    public record Internship(long id,String companyName,String jobCategoryCode,LocalDate startedOn,LocalDate endedOn,String description) {
    }
    public record AwardInput(@NotBlank @Size(max=255) String name,@NotBlank @Size(max=255) String organizer,@Size(max=50000) String description) {
    }
    public record Award(long id,String name,String organizer,String description) {
    }
    public record CourseInput(@NotBlank @Size(max=32) String courseNumber,@NotBlank @Size(max=255) String courseName,@NotNull @Min(1900) @Max(2100) Integer academicYear,@NotBlank @Size(max=32) String semester,@NotBlank @Pattern(regexp="GRADE|P/NP") String evaluationMethod,@NotBlank @Size(max=16) String grade,@DecimalMin("0.0") @DecimalMax("4.5") @Digits(integer=1,fraction=2) BigDecimal gradePoint) {
    }
    public record Course(long id,String courseNumber,String courseName,int academicYear,String semester,String evaluationMethod,String grade,BigDecimal gradePoint) {
    }
    public record RegionGroup(String name, List<Code> regions) {}
    public record Code(String code,String name) {
    }
}
