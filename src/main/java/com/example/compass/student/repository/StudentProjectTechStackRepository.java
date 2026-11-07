package com.example.compass.student.repository;
import com.example.compass.student.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StudentProjectTechStackRepository extends JpaRepository<StudentProjectTechStack,StudentProjectTechStackId> {
    List<StudentProjectTechStack> findAllByIdProjectId(Long projectId);
}
