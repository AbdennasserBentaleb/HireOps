package com.hireops.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "resume_personas")
public class ResumePersona {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserProfile userProfile;

    @Column(nullable = false)
    private String personaName; // e.g., "Frontend Developer", "Manager"

    private String role; // e.g., "Specialized in React & Next.js"

    @Column(columnDefinition = "TEXT")
    private String systemPromptOverride; // Specific prompt for this persona

    private String cvPdfPath; // The specific PDF for this persona

    private boolean isDefault; // Auto-select this if AI is unsure

    public ResumePersona() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public String getPersonaName() {
        return personaName;
    }

    public void setPersonaName(String personaName) {
        this.personaName = personaName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSystemPromptOverride() {
        return systemPromptOverride;
    }

    public void setSystemPromptOverride(String systemPromptOverride) {
        this.systemPromptOverride = systemPromptOverride;
    }

    public String getCvPdfPath() {
        return cvPdfPath;
    }

    public void setCvPdfPath(String cvPdfPath) {
        this.cvPdfPath = cvPdfPath;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
