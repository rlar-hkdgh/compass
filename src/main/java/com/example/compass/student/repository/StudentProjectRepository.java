package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentProject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProjectRepository extends JpaRepository<StudentProject,Long> {
}
