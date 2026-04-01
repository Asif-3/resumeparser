package com.jobportal.resumeparser.repository;

import com.jobportal.resumeparser.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends MongoRepository<Candidate, String> {

    Optional<Candidate> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByResumeHash(String resumeHash);

    Optional<Candidate> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<Candidate> findByStatus(String status);
}