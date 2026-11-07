package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentInternship;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentInternshipRepository extends JpaRepository<StudentInternship,Long> {
}
