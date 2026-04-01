package com.jobportal.resumeparser.model;

/**
 * DTO for per-file upload result.
 * Status values: SUCCESS, DUPLICATE, INVALID, ERROR
 */
public class UploadResult {

    private String fileName;
    private String status;    // SUCCESS, DUPLICATE, INVALID, ERROR
    private String reason;
    private String candidateId;

    public UploadResult() {}

    public UploadResult(String fileName, String status, String reason) {
        this.fileName = fileName;
        this.status = status;
        this.reason = reason;
    }

    public UploadResult(String fileName, String status, String reason, String candidateId) {
        this.fileName = fileName;
        this.status = status;
        this.reason = reason;
        this.candidateId = candidateId;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
}
