package com.example.compass.student.dto;


import com.example.compass.student.entity.OnboardingStep;
import com.example.compass.student.entity.OnboardingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class OnboardingDtos {
    private OnboardingDtos() {}
    public record StepChange(@NotNull OnboardingStep step,@NotNull OnboardingStatus status) {}
    public record Patch(@NotEmpty @Size(max=8) List<@NotNull @Valid StepChange> steps) {}
    public record StepState(OnboardingStep step,OnboardingStatus status) {}
    public record View(List<StepState> steps,OnboardingStep nextStep,boolean finished,
                       int totalSteps,int completedSteps,int skippedSteps) {}
}
