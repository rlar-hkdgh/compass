package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentMajor;
import com.example.compass.student.entity.StudentMajorId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentMajorRepository extends JpaRepository<StudentMajor,StudentMajorId> {
}
