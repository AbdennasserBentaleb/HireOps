package com.hireops.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fullName;
    private String searchKeyword;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(columnDefinition = "TEXT")
    private String emailSignature;

    @Column(name = "min_match_score")
    private Integer minMatchScore = 75;

    @Column(name = "max_job_age_days")
    private Integer maxJobAgeDays = 30; // 0 means any

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumePersona> personas = new ArrayList<>();

    public UserProfile() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getEmailSignature() {
        return emailSignature;
    }

    public void setEmailSignature(String emailSignature) {
        this.emailSignature = emailSignature;
    }

    public List<ResumePersona> getPersonas() {
        return personas;
    }

    public void setPersonas(List<ResumePersona> personas) {
        this.personas = personas;
    }

    public void addPersona(ResumePersona persona) {
        personas.add(persona);
        persona.setUserProfile(this);
    }

    public void removePersona(ResumePersona persona) {
        personas.remove(persona);
        persona.setUserProfile(null);
    }

    public Integer getMinMatchScore() {
        return minMatchScore;
    }

    public void setMinMatchScore(Integer minMatchScore) {
        this.minMatchScore = minMatchScore;
    }

    public Integer getMaxJobAgeDays() {
        return maxJobAgeDays;
    }

    public void setMaxJobAgeDays(Integer maxJobAgeDays) {
        this.maxJobAgeDays = maxJobAgeDays;
    }

    // SMTP Configuration
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private Boolean smtpAuth;
    private Boolean smtpStartTls;

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public Boolean getSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(Boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    public Boolean getSmtpStartTls() {
        return smtpStartTls;
    }

    public void setSmtpStartTls(Boolean smtpStartTls) {
        this.smtpStartTls = smtpStartTls;
    }
}
