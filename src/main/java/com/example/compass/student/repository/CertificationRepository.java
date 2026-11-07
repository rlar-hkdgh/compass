package com.example.compass.student.repository;
import com.example.compass.student.entity.Certification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification,String> {
}
