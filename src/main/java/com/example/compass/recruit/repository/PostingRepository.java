package com.example.compass.recruit.repository;
import com.example.compass.recruit.entity.JobPosting;
import com.example.compass.recruit.entity.PostingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface PostingRepository extends JpaRepository<JobPosting,PostingId>, JpaSpecificationExecutor<JobPosting> {}
