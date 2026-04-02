package com.jobportal.resumeparser.service;

import com.jobportal.resumeparser.model.Candidate;
import com.jobportal.resumeparser.model.UploadResult;
import com.jobportal.resumeparser.parser.ResumeParser;
import com.jobportal.resumeparser.repository.CandidateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private ResumeParser resumeParser;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    // =========================
    // HASH GENERATION
    // =========================
    public String generateHash(String text) {
        try {
            if (text == null) text = "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash generation failed");
        }
    }

    // =========================
    // BULK UPLOAD & PROCESS
    // =========================
    public List<UploadResult> uploadAndProcess(MultipartFile[] files) {

        // ── PRE-CHECK MONGODB CONNECTION ──
        try {
            candidateRepository.count();
        } catch (Exception e) {
            log.error("DB_PRECHECK_FAILED | error={}", e.getMessage());
            throw new RuntimeException("Database connection lost. Data not saved.");
        }

        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {

            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "unknown";

            log.info("UPLOAD_START | file={}", originalName);

            // ── 1. FILE FORMAT VALIDATION ──
            String lowerName = originalName.toLowerCase();
            String extension = lowerName.contains(".")
                    ? lowerName.substring(lowerName.lastIndexOf("."))
                    : "";

            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                log.warn("INVALID_FORMAT | file={} | ext={}", originalName, extension);
                results.add(new UploadResult(originalName, "INVALID",
                        "Unsupported format. Only PDF, DOC, DOCX allowed."));
                continue;
            }

            // ── 2. FILE SIZE VALIDATION (5MB) ──
            if (file.getSize() > 5 * 1024 * 1024) {
                log.warn("FILE_TOO_LARGE | file={} | size={}MB",
                        originalName, file.getSize() / (1024 * 1024));
                results.add(new UploadResult(originalName, "INVALID",
                        "File too large. Maximum 5MB allowed."));
                continue;
            }

            try {
                // ── 3. EXTRACT TEXT WITH TIKA ──
                File tempFile = File.createTempFile("resume_", extension);
                file.transferTo(tempFile);

                String extractedText;
                try {
                    extractedText = resumeParser.extractText(tempFile);
                } catch (Exception e) {
                    log.error("PARSE_FAILED | file={} | error={}", originalName, e.getMessage());
                    results.add(new UploadResult(originalName, "ERROR",
                            "Failed to parse file: " + e.getMessage()));
                    tempFile.delete();
                    continue;
                } finally {
                    if (tempFile.exists() && !tempFile.delete()) {
                        tempFile.deleteOnExit();
                    }
                }

                if (extractedText == null || extractedText.trim().isEmpty()) {
                    extractedText = originalName;
                }

                // ── 4. PARSE FIELDS ──
                String parsedName = resumeParser.extractName(extractedText);
                String parsedEmail = resumeParser.extractEmail(extractedText);
                String parsedPhone = resumeParser.extractPhone(extractedText);
                String parsedSkills = resumeParser.extractSkills(extractedText);
                String parsedEducation = resumeParser.extractEducation(extractedText);
                String parsedExperience = resumeParser.extractExperience(extractedText);
                String parsedJobRole = resumeParser.extractJobRole(extractedText);
                String parsedLocation = resumeParser.extractLocation(extractedText);
                String parsedLinkedin = resumeParser.extractLinkedIn(extractedText);
                String parsedGithub = resumeParser.extractGithub(extractedText);
                String parsedCerts = resumeParser.extractCertifications(extractedText);

                // Normalize email
                if (parsedEmail != null) {
                    parsedEmail = parsedEmail.trim().toLowerCase();
                }

                // Normalize phone
                if (parsedPhone != null) {
                    parsedPhone = parsedPhone.replaceAll("[\\s\\-]", "").trim();
                }

                log.info("PARSED | file={} | name={} | email={} | phone={}",
                        originalName, parsedName, parsedEmail, parsedPhone);

                // ── 5. DUPLICATE DETECTION ──
                // Check by email
                boolean hasValidEmail = parsedEmail != null
                        && !parsedEmail.isEmpty()
                        && !parsedEmail.equals("null");

                boolean hasValidPhone = parsedPhone != null
                        && !parsedPhone.isEmpty()
                        && !parsedPhone.equals("null");

                if (hasValidEmail) {
                    final String emailCheck = parsedEmail;
                    boolean emailDup = candidateRepository.findAll().stream()
                            .anyMatch(c -> c.getEmail() != null
                                    && !c.getEmail().equalsIgnoreCase("null")
                                    && c.getEmail().equalsIgnoreCase(emailCheck));
                    if (emailDup) {
                        log.warn("DUPLICATE_EMAIL | file={} | email={}", originalName, parsedEmail);
                        results.add(new UploadResult(originalName, "DUPLICATE",
                                "Duplicate detected — email '" + parsedEmail + "' already exists."));
                        continue;
                    }
                }

                // Check by phone
                if (hasValidPhone) {
                    final String phoneCheck = parsedPhone;
                    boolean phoneDup = candidateRepository.findAll().stream()
                            .anyMatch(c -> c.getPhone() != null
                                    && !c.getPhone().equalsIgnoreCase("null")
                                    && c.getPhone().replaceAll("[\\s\\-]", "")
                                    .equals(phoneCheck));
                    if (phoneDup) {
                        log.warn("DUPLICATE_PHONE | file={} | phone={}", originalName, parsedPhone);
                        results.add(new UploadResult(originalName, "DUPLICATE",
                                "Duplicate detected — phone '" + parsedPhone + "' already exists."));
                        continue;
                    }
                }

                // Check by hash (fallback)
                String normalizedText = extractedText.toLowerCase().replaceAll("\\s+", "");
                String hash = generateHash(normalizedText);

                if (!hasValidEmail && !hasValidPhone) {
                    boolean hashDup = candidateRepository.findAll().stream()
                            .anyMatch(c -> c.getResumeHash() != null
                                    && c.getResumeHash().equals(hash));
                    if (hashDup) {
                        log.warn("DUPLICATE_HASH | file={}", originalName);
                        results.add(new UploadResult(originalName, "DUPLICATE",
                                "Duplicate detected — identical resume content already exists."));
                        continue;
                    }
                }

                // ── 6. SAVE TO CANDIDATES COLLECTION (status = Pending) ──
                Candidate candidate = new Candidate();
                candidate.setName(parsedName);
                candidate.setEmail(hasValidEmail ? parsedEmail : null);
                candidate.setPhone(hasValidPhone ? parsedPhone : null);
                candidate.setSkills(parsedSkills);
                candidate.setEducation(parsedEducation);
                candidate.setExperience(parsedExperience);
                candidate.setJobRole(parsedJobRole);
                candidate.setLocation(parsedLocation);
                candidate.setLinkedin(parsedLinkedin);
                candidate.setGithub(parsedGithub);
                candidate.setCertifications(parsedCerts);
                candidate.setResumeText(extractedText);
                candidate.setResumeHash(hash);
                candidate.setOriginalFileName(originalName);
                candidate.setStatus("Pending");
                candidate.setVerified(false);
                candidate.setUploadedAt(LocalDateTime.now());

                Candidate saved;

                try {
                    saved = candidateRepository.save(candidate);
                } catch (Exception e) {
                    // ❌ DB failed
                    throw new RuntimeException("Database connection lost. Data not saved.");
                }

// ✅ Only runs if DB success
                results.add(new UploadResult(originalName, "SUCCESS",
                        "Parsed and saved successfully.", saved.getId()));



                log.info("UPLOAD_SUCCESS | file={} | id={} | status=Pending",
                        originalName, saved.getId());


            } catch (RuntimeException e) {
                if ("Database connection lost. Data not saved.".equals(e.getMessage()) ||
                        e.getClass().getName().contains("Mongo") ||
                        e.getClass().getName().contains("DataAccess")) {
                    throw new RuntimeException("Database connection lost. Data not saved.", e);
                }
                log.error("PROCESS_ERROR | file={} | error={}", originalName, e.getMessage());
                results.add(new UploadResult(originalName, "ERROR",
                        "Processing failed: " + e.getMessage()));
            } catch (Exception e) {
                if (e.getClass().getName().contains("Mongo") || e.getClass().getName().contains("DataAccess")) {
                    throw new RuntimeException("Database connection lost. Data not saved.", e);
                }
                log.error("PROCESS_ERROR | file={} | error={}", originalName, e.getMessage());
                results.add(new UploadResult(originalName, "ERROR",
                        "Processing failed: " + e.getMessage()));
            }
        }

        return results;
    }

    // =========================
    // GET ALL CANDIDATES
    // =========================
    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAll();
    }

    // =========================
    // GET PENDING CANDIDATES
    // =========================
    public List<Candidate> getPendingCandidates() {
        return candidateRepository.findByStatus("Pending");
    }

    // =========================ma
    // GET VERIFIED CANDIDATES
    // =========================
    public List<Candidate> getVerifiedCandidates() {
        return candidateRepository.findByStatus("Verified");
    }

    // =========================
    // VERIFY (PENDING → VERIFIED)
    // =========================
    public Candidate verifyCandidate(String id) {
        Candidate c = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        // Re-check duplicate before verifying
        String email = c.getEmail();
        if (email != null && !email.isEmpty() && !email.equals("null")) {
            boolean emailDup = candidateRepository.findAll().stream()
                    .anyMatch(existing ->
                            !existing.getId().equals(c.getId())
                                    && existing.getEmail() != null
                                    && existing.getEmail().equalsIgnoreCase(email)
                                    && "Verified".equals(existing.getStatus()));
            if (emailDup) {
                log.warn("VERIFY_DUPLICATE_EMAIL | id={} | email={}", id, email);
                throw new RuntimeException(
                        "Cannot verify — email '" + email + "' already exists in verified candidates.");
            }
        }

        String phone = c.getPhone();
        if (phone != null && !phone.isEmpty() && !phone.equals("null")) {
            boolean phoneDup = candidateRepository.findAll().stream()
                    .anyMatch(existing ->
                            !existing.getId().equals(c.getId())
                                    && existing.getPhone() != null
                                    && existing.getPhone().replaceAll("[\\s\\-]", "")
                                    .equals(phone.replaceAll("[\\s\\-]", ""))
                                    && "Verified".equals(existing.getStatus()));
            if (phoneDup) {
                log.warn("VERIFY_DUPLICATE_PHONE | id={} | phone={}", id, phone);
                throw new RuntimeException(
                        "Cannot verify — phone '" + phone + "' already exists in verified candidates.");
            }
        }

        c.setVerified(true);
        c.setStatus("Verified");
        c.setVerifiedAt(LocalDateTime.now());

        log.info("VERIFIED | id={} | name={}", id, c.getName());
        return candidateRepository.save(c);
    }

    // =========================
    // BULK VERIFY
    // =========================
    public List<Map<String, String>> bulkVerify(List<String> ids) {
        List<Map<String, String>> results = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> result = new HashMap<>();
            result.put("id", id);
            try {
                Candidate verified = verifyCandidate(id);
                result.put("status", "SUCCESS");
                result.put("name", verified.getName());
            } catch (RuntimeException e) {
                result.put("status", "FAILED");
                result.put("reason", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    // =========================
    // UPDATE CANDIDATE
    // =========================
    public Candidate updateCandidate(String id, Candidate updated) {
        Candidate existing = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setSkills(updated.getSkills());
        existing.setEducation(updated.getEducation());
        existing.setExperience(updated.getExperience());
        existing.setJobRole(updated.getJobRole());
        existing.setLocation(updated.getLocation());
        existing.setLinkedin(updated.getLinkedin());
        existing.setGithub(updated.getGithub());
        existing.setCertifications(updated.getCertifications());

        log.info("UPDATED | id={} | name={}", id, existing.getName());
        return candidateRepository.save(existing);
    }

    // =========================
    // UPDATE STATUS
    // =========================
    public Candidate updateStatus(String id, String status) {
        Candidate c = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        c.setStatus(status);
        log.info("STATUS_CHANGED | id={} | status={}", id, status);
        return candidateRepository.save(c);
    }

    // =========================
    // SAVE CANDIDATE (MANUAL)
    // =========================
    public Candidate saveCandidate(Candidate candidate) {
        String email = candidate.getEmail();
        if (email != null) {
            email = email.trim().toLowerCase();
            candidate.setEmail(email);
        }

        if (email != null && !email.isEmpty() && !email.equals("null")) {
            final String emailCheck = email;
            boolean exists = candidateRepository.findAll().stream()
                    .anyMatch(c -> c.getEmail() != null
                            && c.getEmail().equalsIgnoreCase(emailCheck));
            if (exists) {
                throw new RuntimeException("Duplicate email: " + email);
            }
        }

        return candidateRepository.save(candidate);
    }

    // =========================
    // DELETE SINGLE
    // =========================
    public void deleteCandidate(String id) {
        log.info("DELETED | id={}", id);
        candidateRepository.deleteById(id);
    }

    // =========================
    // DELETE BULK
    // =========================
    public void deleteCandidates(List<String> ids) {
        log.info("BULK_DELETE | count={}", ids.size());
        candidateRepository.deleteAllById(ids);
    }
}