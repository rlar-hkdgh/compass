package com.example.compass.student.entity;

/** Last fetch attempt only; a failed attempt may retain an earlier successful README. */
public enum ReadmeAttemptStatus {
    SUCCESS, NOT_FOUND, RATE_LIMITED, FAILED
}
