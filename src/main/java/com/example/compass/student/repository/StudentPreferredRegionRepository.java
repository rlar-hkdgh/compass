package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentPreferredRegion;
import com.example.compass.student.entity.StudentPreferredRegionId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPreferredRegionRepository extends JpaRepository<StudentPreferredRegion,StudentPreferredRegionId> {
}
