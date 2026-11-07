package com.example.compass.student.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CareerGoalDtos {
    private CareerGoalDtos() {}
    public enum CompanySize { LARGE, MID, STARTUP, SMALL }
    public record CareerGoalInput(
        @NotBlank @Size(max=32) String jobCategoryCode,
        @NotNull @Size(min=1,max=4) List<@NotNull CompanySize> companySizes,
        @NotNull @PositiveOrZero Long targetSalary,
        @NotNull @Size(min=1,max=100) List<@NotBlank String> regionCodes,
        @NotBlank @Size(max=255) String targetCompany,
        @NotBlank @Pattern(regexp="(19[0-9]{2}|20[0-9]{2}|2100)-(0[1-9]|1[0-2])") String targetDate) {}

    public record CareerGoal(String jobCategoryCode, List<CompanySize> companySizes, long targetSalary,
        List<String> regionCodes, String targetCompany, String targetDate) {}
}
