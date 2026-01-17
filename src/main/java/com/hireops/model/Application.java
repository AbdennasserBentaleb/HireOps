package com.hireops.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "cv_pdf_path", length = 512)
    private String cvPdfPath;

    @Column(name = "cover_letter_pdf_path", length = 10000)
    private String coverLetterPdfPath;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public void setJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }

    public String getCvPdfPath() {
        return cvPdfPath;
    }

    public void setCvPdfPath(String cvPdfPath) {
        this.cvPdfPath = cvPdfPath;
    }

    public String getCoverLetterPdfPath() {
        return coverLetterPdfPath;
    }

    public void setCoverLetterPdfPath(String coverLetterPdfPath) {
        this.coverLetterPdfPath = coverLetterPdfPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
