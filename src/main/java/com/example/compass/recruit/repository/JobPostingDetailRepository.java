package com.example.compass.recruit.repository;
import com.example.compass.recruit.entity.JobPostingDetail;
import com.example.compass.recruit.entity.PostingId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobPostingDetailRepository extends JpaRepository<JobPostingDetail,PostingId> {}
