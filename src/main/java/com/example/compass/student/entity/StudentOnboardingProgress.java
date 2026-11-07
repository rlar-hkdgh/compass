package com.example.compass.student.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Stored decisions only. NOT_STARTED is represented by an absent row. */
@Entity
@Table(name="student_onboarding_step")
public class StudentOnboardingProgress {
    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable=false,length=16)
    private OnboardingStatus status;

    public StudentOnboardingProgress() {}
    public Key getId() { return id; }
    public void setId(Key id) { this.id=id; }
    public OnboardingStatus getStatus() { return status; }
    public void setStatus(OnboardingStatus status) { this.status=status; }

    @Embeddable
    public static class Key implements Serializable {
        private static final long serialVersionUID=1L;
        @Column(name="student_id",nullable=false)
        private Long studentId;
        @Enumerated(EnumType.STRING)
        @Column(name="step_code",nullable=false,length=32)
        private OnboardingStep stepCode;

        public Key() {}
        public Key(Long studentId,OnboardingStep stepCode) { this.studentId=studentId;this.stepCode=stepCode; }
        public Long getStudentId() { return studentId; }
        public OnboardingStep getStepCode() { return stepCode; }
        @Override public boolean equals(Object other) {
            return this==other || other instanceof Key that && Objects.equals(studentId,that.studentId) && stepCode==that.stepCode;
        }
        @Override public int hashCode() { return Objects.hash(studentId,stepCode); }
    }
}
