package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentCourse;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCourseRepository extends JpaRepository<StudentCourse,Long> {
}
