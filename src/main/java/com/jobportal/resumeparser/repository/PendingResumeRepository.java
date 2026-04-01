package com.jobportal.resumeparser.repository;

import com.jobportal.resumeparser.model.PendingResume;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PendingResumeRepository extends MongoRepository<PendingResume, String> {


    List<PendingResume> findByStatus(String status);


    boolean existsByEmail(String email);

    boolean existsByResumeHash(String resumeHash);
}