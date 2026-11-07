package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentPreferredEmploymentType;
import com.example.compass.student.entity.StudentPreferredEmploymentTypeId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPreferredEmploymentTypeRepository extends JpaRepository<StudentPreferredEmploymentType,StudentPreferredEmploymentTypeId> {
}
