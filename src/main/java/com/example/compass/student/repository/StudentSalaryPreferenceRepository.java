package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentSalaryPreference;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentSalaryPreferenceRepository extends JpaRepository<StudentSalaryPreference,Long> {
}
