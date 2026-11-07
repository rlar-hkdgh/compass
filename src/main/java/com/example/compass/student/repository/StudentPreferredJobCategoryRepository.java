package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentPreferredJobCategory;
import com.example.compass.student.entity.StudentPreferredJobCategoryId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPreferredJobCategoryRepository extends JpaRepository<StudentPreferredJobCategory,StudentPreferredJobCategoryId> {
}
