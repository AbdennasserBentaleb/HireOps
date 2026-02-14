package com.hireops.service;

import com.hireops.model.Application;
import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.ApplicationRepository;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import com.hireops.model.UserProfile;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final UserProfileRepository userProfileRepository;
    private final JavaMailSender javaMailSender;

    public DispatchService(JobPostingRepository jobPostingRepository,
            ApplicationRepository applicationRepository,
            UserProfileRepository userProfileRepository,
            JavaMailSender javaMailSender) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.userProfileRepository = userProfileRepository;
        this.javaMailSender = javaMailSender;
    }

    @org.springframework.transaction.annotation.Transactional
    public void dispatchApplication(java.util.UUID jobId, String overrideEmail) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        List<Application> apps = applicationRepository.findByJobPostingId(job.getId());
        if (apps.isEmpty()) {
            log.warn("No application assets found for job {}", job.getId());
            return;
        }

        Application application = apps.get(0);

        // Priority: user-edited form value > API-extracted email > heuristic domain
        // guess
        String recruiterEmail;
        if (overrideEmail != null && !overrideEmail.isBlank()) {
            recruiterEmail = overrideEmail;
        } else if (job.getEmployerEmail() != null && !job.getEmployerEmail().isEmpty()) {
            recruiterEmail = job.getEmployerEmail();
        } else {
            recruiterEmail = predictEmployerEmail(job.getCompany());
        }

        List<UserProfile> profiles = userProfileRepository.findAll();
        UserProfile profile = profiles.isEmpty() ? new UserProfile() : profiles.get(0);
        String signature = profile.getEmailSignature() != null ? profile.getEmailSignature()
                : "Mit freundlichen Grüßen,\nJohn Doe";

        try {
            JavaMailSender senderToUse = buildMailSender(profile);
            if (senderToUse == null) {
                log.warn("SMTP settings not configured in profile. Falling back to default Spring JavaMailSender.");
                senderToUse = this.javaMailSender;
            }
            sendEmail(senderToUse, recruiterEmail, application.getCvPdfPath(), application.getCoverLetterPdfPath(),
                    signature);

            job.setStatus(JobStatus.APPLIED);
            job.setEmployerEmail(recruiterEmail);
            jobPostingRepository.save(job);
            log.info("Successfully processed application dispatch to {} for job {}", recruiterEmail, job.getId());
        } catch (Exception e) {
            log.error("Failed to send email to {}", recruiterEmail, e);
            throw new RuntimeException("Email dispatch failed. Please check your SMTP configuration.", e);
        }
    }

    private JavaMailSender buildMailSender(UserProfile profile) {
        if (profile.getSmtpHost() == null || profile.getSmtpHost().trim().isEmpty()) {
            return null;
        }
        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost(profile.getSmtpHost());
        mailSender.setPort(profile.getSmtpPort() != null ? profile.getSmtpPort() : 587);
        mailSender.setUsername(profile.getSmtpUsername());
        mailSender.setPassword(profile.getSmtpPassword());

        java.util.Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", profile.getSmtpAuth() != null && profile.getSmtpAuth() ? "true" : "false");
        props.put("mail.smtp.starttls.enable",
                profile.getSmtpStartTls() != null && profile.getSmtpStartTls() ? "true" : "false");

        return mailSender;
    }

    private void sendEmail(JavaMailSender sender, String to, String cvPath, String clPath, String signature)
            throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(to);
        helper.setSubject("Bewerbung als Software Engineer - Blue Card Profile");
        helper.setText(
                "Sehr geehrte Damen und Herren,\n\nanbei erhalten Sie meine Bewerbungsunterlagen.\n\n" + signature);

        if (cvPath != null) {
            FileSystemResource cvDoc = new FileSystemResource(new File(cvPath));
            if (cvDoc.exists())
                helper.addAttachment("Lebenslauf.pdf", cvDoc);
        }

        if (clPath != null) {
            FileSystemResource clDoc = new FileSystemResource(new File(clPath));
            if (clDoc.exists())
                helper.addAttachment("Anschreiben.pdf", clDoc);
        }

        sender.send(message);
    }

    private String predictEmployerEmail(String companyName) {
        // Heuristically generate a probable HR email address based on the company name.
        String domain = companyName.toLowerCase().replaceAll("[^a-z0-9]", "") + ".de";
        return "hr@" + domain;
    }
}
