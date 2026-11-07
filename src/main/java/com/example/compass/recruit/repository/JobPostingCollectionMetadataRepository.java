package com.example.compass.recruit.repository;
import com.example.compass.recruit.entity.JobPostingCollectionMetadata;
import com.example.compass.recruit.entity.PostingId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobPostingCollectionMetadataRepository extends JpaRepository<JobPostingCollectionMetadata,PostingId> {}
