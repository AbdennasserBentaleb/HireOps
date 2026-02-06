package com.hireops.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_id", unique = true, nullable = false)
    private String referenceId;

    /**
     * The hashId from the Bundesagentur API — used to construct the job portal URL.
     */
    @Column(name = "hash_id")
    private String hashId;

    @Column(length = 2000, nullable = false)
    private String title;

    @Column(length = 2000, nullable = false)
    private String company;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.FETCHED;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "employer_email")
    private String employerEmail;

    /** User-added CRM note for this job (e.g. "Met recruiter at event"). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** When the AI scored this job — used for weekly stats. */
    @Column(name = "scored_at")
    private LocalDateTime scoredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getHashId() {
        return hashId;
    }

    public void setHashId(String hashId) {
        this.hashId = hashId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public String getEmployerEmail() {
        return employerEmail;
    }

    public void setEmployerEmail(String employerEmail) {
        this.employerEmail = employerEmail;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(LocalDateTime scoredAt) {
        this.scoredAt = scoredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** Returns the direct link to this job on the Arbeitsagentur portal. */
    public String getPortalUrl() {
        if (hashId != null && !hashId.isBlank()) {
            return "https://www.arbeitsagentur.de/jobsuche/jobdetail/" + hashId;
        }
        return null;
    }
}
