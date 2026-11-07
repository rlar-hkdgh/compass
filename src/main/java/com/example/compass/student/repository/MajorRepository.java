package com.example.compass.student.repository;
import com.example.compass.student.entity.Major;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major,String> {
}
