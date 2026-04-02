package com.jobportal.resumeparser.controller;

import com.jobportal.resumeparser.model.Candidate;
import com.jobportal.resumeparser.model.UploadResult;
import com.jobportal.resumeparser.service.ResumeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    @Autowired
    private ResumeService service;

    // =========================
    // BULK UPLOAD RESUMES
    // =========================
    @PostMapping("/upload")
    public ResponseEntity<?> uploadResumes(@RequestParam("file") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", "No files uploaded"));
        }

        log.info("UPLOAD_REQUEST | fileCount={}", files.length);

        try {
            List<UploadResult> results = service.uploadAndProcess(files);

            // Categorize results for response
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalFiles", files.length);

            long successCount = results.stream()
                    .filter(r -> "SUCCESS".equals(r.getStatus())).count();
            long duplicateCount = results.stream()
                    .filter(r -> "DUPLICATE".equals(r.getStatus())).count();
            long invalidCount = results.stream()
                    .filter(r -> "INVALID".equals(r.getStatus())).count();
            long errorCount = results.stream()
                    .filter(r -> "ERROR".equals(r.getStatus())).count();

            response.put("successCount", successCount);
            response.put("duplicateCount", duplicateCount);
            response.put("invalidCount", invalidCount);
            response.put("errorCount", errorCount);

            log.info("UPLOAD_COMPLETE | success={} | duplicate={} | invalid={} | error={}",
                    successCount, duplicateCount, invalidCount, errorCount);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if ("Database connection lost. Data not saved.".equals(e.getMessage())) {
                log.error("UPLOAD_DB_ERROR | {}", e.getMessage());
                Map<String, String> errResponse = new HashMap<>();
                errResponse.put("status", "ERROR");
                errResponse.put("message", "Database connection lost. Data not saved.");
                return ResponseEntity.status(500).body(errResponse);
            }
            throw e;
        }
    }

    // =========================
    // GET PENDING CANDIDATES
    // =========================
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingCandidates() {
        List<Candidate> list = service.getPendingCandidates();
        list.forEach(c -> {
            if (c.getEmail() != null) c.setEmail(c.getEmail().trim().toLowerCase());
        });
        return ResponseEntity.ok(list);
    }

    // =========================
    // GET ALL CANDIDATES (verified)
    // =========================
    @GetMapping("/candidates")
    public ResponseEntity<?> getCandidates() {
        List<Candidate> list = service.getAllCandidates();
        list.forEach(c -> {
            if (c.getEmail() != null) c.setEmail(c.getEmail().trim().toLowerCase());
        });
        return ResponseEntity.ok(list);
    }

    // =========================
    // VERIFY SINGLE
    // =========================
    @PostMapping("/verify/{id}")
    public ResponseEntity<?> verifyCandidate(@PathVariable String id) {
        try {
            Candidate candidate = service.verifyCandidate(id);
            if (candidate.getEmail() != null) {
                candidate.setEmail(candidate.getEmail().trim().toLowerCase());
            }
            return ResponseEntity.ok(candidate);
        } catch (RuntimeException e) {
            log.error("VERIFY_FAILED | id={} | error={}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // =========================
    // BULK VERIFY
    // =========================
    @PostMapping("/candidates/bulk-verify")
    public ResponseEntity<?> bulkVerify(@RequestBody List<String> ids) {
        log.info("BULK_VERIFY_REQUEST | count={}", ids.size());
        List<Map<String, String>> results = service.bulkVerify(ids);
        return ResponseEntity.ok(results);
    }

    // =========================
    // DELETE PENDING
    // =========================
    @DeleteMapping("/pending/{id}")
    public ResponseEntity<?> deletePending(@PathVariable String id) {
        try {
            service.deleteCandidate(id);
            return ResponseEntity.ok(
                    Collections.singletonMap("message", "Pending resume deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // =========================
    // APPROVE (MANUAL)
    // =========================
    @PostMapping("/approve")
    public ResponseEntity<?> approveCandidate(@RequestBody Candidate candidate) {
        try {
            candidate.setVerified(true);
            candidate.setStatus("Verified");
            if (candidate.getEmail() != null) {
                candidate.setEmail(candidate.getEmail().trim().toLowerCase());
            }
            Candidate saved = service.saveCandidate(candidate);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // =========================
    // UPDATE CANDIDATE
    // =========================
    @PutMapping("/candidate/{id}")
    public ResponseEntity<?> updateCandidate(
            @PathVariable String id,
            @RequestBody Candidate updatedCandidate) {
        if (updatedCandidate.getEmail() != null) {
            updatedCandidate.setEmail(updatedCandidate.getEmail().trim().toLowerCase());
        }
        Candidate updated = service.updateCandidate(id, updatedCandidate);
        return ResponseEntity.ok(updated);
    }

    // =========================
    // VERIFY EXISTING CANDIDATE
    // =========================
    @PutMapping("/candidate/{id}/verify")
    public ResponseEntity<?> verifyExistingCandidate(@PathVariable String id) {
        try {
            Candidate verified = service.verifyCandidate(id);
            return ResponseEntity.ok(verified);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // =========================
    // UPDATE STATUS
    // =========================
    @PutMapping("/candidate/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestParam String status) {
        Candidate updated = service.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    // =========================
    // DELETE SINGLE
    // =========================
    @DeleteMapping("/candidate/{id}")
    public ResponseEntity<?> deleteCandidate(@PathVariable String id) {
        service.deleteCandidate(id);
        return ResponseEntity.ok(
                Collections.singletonMap("message", "Candidate deleted successfully"));
    }

    // =========================
    // DELETE BULK
    // =========================
    @DeleteMapping("/candidates/bulk")
    public ResponseEntity<?> deleteCandidates(@RequestBody List<String> ids) {
        service.deleteCandidates(ids);
        return ResponseEntity.ok(
                Collections.singletonMap("message", ids.size() + " candidates deleted"));
    }

    // =========================
    // FRONTEND LOGGING ENDPOINT
    // =========================
    @PostMapping("/log")
    public ResponseEntity<?> frontendLog(@RequestBody Map<String, String> payload) {
        String level = payload.getOrDefault("level", "INFO");
        String message = payload.getOrDefault("message", "");
        String source = payload.getOrDefault("source", "frontend");

        switch (level.toUpperCase()) {
            case "ERROR":
                log.error("FRONTEND | source={} | {}", source, message);
                break;
            case "WARN":
                log.warn("FRONTEND | source={} | {}", source, message);
                break;
            default:
                log.info("FRONTEND | source={} | {}", source, message);
        }

        return ResponseEntity.ok(Collections.singletonMap("logged", true));
    }
}