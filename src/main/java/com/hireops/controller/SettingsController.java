package com.hireops.controller;

import com.hireops.model.UserProfile;
import com.hireops.model.ResumePersona;
import com.hireops.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final UserProfileRepository userProfileRepository;

    @Value("${app.settings.pdf-storage-path:/tmp/hireops_pdfs}")
    private String pdfStoragePath;

    public SettingsController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
        try {
            Files.createDirectories(Paths.get("/tmp/hireops_pdfs"));
        } catch (IOException e) {
            log.error("Could not create PDF storage directory", e);
        }
    }

    @GetMapping
    public String viewSettings(Model model) {
        List<UserProfile> profiles = userProfileRepository.findAll();
        UserProfile profile = profiles.isEmpty() ? new UserProfile() : profiles.get(0);

        // Provide defaults if new
        if (profile.getSystemPrompt() == null) {
            profile.setSystemPrompt(
                    "You are a professional IT job matcher and cover letter writer. Respond ONLY in valid JSON matching the schema. Generate the coverLetterMarkdown in B1/B2 level German suitable for a Software Engineer applying in Germany.");
        }
        if (profile.getSearchKeyword() == null) {
            profile.setSearchKeyword("Java");
        }

        String cvPdfPath = null;
        if (profile.getPersonas() != null) {
            cvPdfPath = profile.getPersonas().stream()
                    .filter(ResumePersona::isDefault)
                    .map(ResumePersona::getCvPdfPath)
                    .findFirst()
                    .orElse(null);
        }

        model.addAttribute("profile", profile);
        model.addAttribute("cvPdfPath", cvPdfPath);
        return "settings";
    }

    @PostMapping
    public String saveSettings(
            @RequestParam(required = false) UUID id,
            @RequestParam String fullName,
            @RequestParam String searchKeyword,
            @RequestParam String systemPrompt,
            @RequestParam String emailSignature,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(required = false, defaultValue = "false") Boolean smtpAuth,
            @RequestParam(required = false, defaultValue = "false") Boolean smtpStartTls,
            @RequestParam("cvFile") MultipartFile cvFile) {

        UserProfile profile;
        if (id != null) {
            profile = userProfileRepository.findById(id).orElse(new UserProfile());
        } else {
            List<UserProfile> profiles = userProfileRepository.findAll();
            profile = profiles.isEmpty() ? new UserProfile() : profiles.get(0);
        }

        profile.setFullName(fullName);
        profile.setSearchKeyword(searchKeyword);
        profile.setSystemPrompt(systemPrompt);
        profile.setEmailSignature(emailSignature);

        profile.setSmtpHost(smtpHost);
        profile.setSmtpPort(smtpPort);
        profile.setSmtpUsername(smtpUsername);
        if (smtpPassword != null && !smtpPassword.trim().isEmpty()) {
            profile.setSmtpPassword(smtpPassword);
        }
        profile.setSmtpAuth(smtpAuth);
        profile.setSmtpStartTls(smtpStartTls);

        if (!cvFile.isEmpty()) {
            try {
                Path storageDir = Paths.get(pdfStoragePath);
                Files.createDirectories(storageDir);

                String originalFilename = cvFile.getOriginalFilename();
                String ext = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    ext = originalFilename.substring(originalFilename.lastIndexOf("."));
                }

                String newFileName = "cv_" + UUID.randomUUID() + ext;
                Path destPath = storageDir.resolve(newFileName);

                Files.copy(cvFile.getInputStream(), destPath, StandardCopyOption.REPLACE_EXISTING);

                // Find existing default or create new one
                ResumePersona persona = profile.getPersonas().stream()
                        .filter(ResumePersona::isDefault)
                        .findFirst()
                        .orElse(null);

                if (persona == null) {
                    persona = new ResumePersona();
                    persona.setPersonaName("Default Profile");
                    persona.setDefault(true);
                    profile.addPersona(persona);
                }

                persona.setCvPdfPath(destPath.toString());
                persona.setSystemPromptOverride(profile.getSystemPrompt());

            } catch (IOException e) {
                log.error("Failed to save uploaded CV file", e);
            }
        }

        userProfileRepository.save(profile);

        return "redirect:/settings?success";
    }
}
