package com.jobportal.resumeparser.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "pending_resumes")
public class PendingResume {

    @Id
    private String id;

    private String name;
    private String email;
    private String phone;
    private String skills;
    private String education;
    private String experience;
    private String jobRole;
    private String location;
    private String linkedin;
    private String github;
    private String certifications;

    private boolean verified;
    private String status;

    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;

    // 🔥 NEW FIELDS (VERY IMPORTANT)
    private String resumeText;   // extracted full text
    private String resumeHash;   // hash for duplicate detection

    // ===== CONSTRUCTOR =====
    public PendingResume() {
        this.verified = false;
        this.status = "Pending";
        this.uploadedAt = LocalDateTime.now();
    }

    // ===== GETTERS & SETTERS =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLinkedin() { return linkedin; }
    public void setLinkedin(String linkedin) { this.linkedin = linkedin; }

    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }

    public String getCertifications() { return certifications; }
    public void setCertifications(String certifications) { this.certifications = certifications; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }


    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getResumeHash() {
        return resumeHash;
    }

    public void setResumeHash(String resumeHash) {
        this.resumeHash = resumeHash;
    }
}