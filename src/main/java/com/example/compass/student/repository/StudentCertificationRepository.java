package com.example.compass.student.repository;
import com.example.compass.student.entity.StudentCertification;
import com.example.compass.student.entity.StudentCertificationId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationRepository extends JpaRepository<StudentCertification,StudentCertificationId> {
}
