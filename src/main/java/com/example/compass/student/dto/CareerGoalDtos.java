package com.example.compass.student.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CareerGoalDtos {
    private CareerGoalDtos() {}
    public enum CompanySize { LARGE, MID, STARTUP, SMALL }
    public enum CareerType { NEW, EXPERIENCED }
    public record CareerGoalInput(
        @NotBlank @Size(max=32) String jobCategoryCode,
        @Size(min=1,max=4) List<@NotNull CompanySize> companySizes, CompanySize companySize,
        @NotNull @PositiveOrZero Long targetSalary,
        @NotNull @Size(min=1,max=100) List<@NotBlank String> regionCodes,
        CareerType careerType,
        @NotBlank @Size(max=255) String targetCompany,@Size(max=255) String targetRole,
        @NotBlank @Pattern(regexp="(19[0-9]{2}|20[0-9]{2}|2100)-(0[1-9]|1[0-2])") String targetDate) {}

    public record CareerGoal(String jobCategoryCode, String legacyJobField, CompanySize companySize, List<CompanySize> companySizes,
        long targetSalary, List<String> regionCodes, CareerType careerType, String targetCompany,
        String targetRole, String targetDate) {}
}
