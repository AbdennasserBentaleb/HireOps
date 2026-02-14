package com.hireops.service;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.JobPostingRepository;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

@Service
public class ImapEmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(ImapEmailSyncService.class);

    private final JobPostingRepository jobPostingRepository;
    private final RejectionLearningProtocol rejectionLearningProtocol;

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    public ImapEmailSyncService(JobPostingRepository jobPostingRepository,
            RejectionLearningProtocol rejectionLearningProtocol) {
        this.jobPostingRepository = jobPostingRepository;
        this.rejectionLearningProtocol = rejectionLearningProtocol;
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void syncEmails() {
        if (mailUsername == null || mailUsername.isEmpty() || mailPassword == null || mailPassword.isEmpty()) {
            log.warn(
                    "IMAP Sync skipped: Mail credentials not configured in application.yml. Add spring.mail.username to activate.");
            return;
        }

        log.info("Starting IMAP Email Sync for incoming recruiter messages...");

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", mailHost);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.timeout", "10000");

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(mailHost, mailUsername, mailPassword);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            List<JobPosting> activeJobs = jobPostingRepository.findAll();

            for (Message message : messages) {
                String subject = message.getSubject() != null ? message.getSubject().toLowerCase() : "";
                String from = message.getFrom()[0].toString().toLowerCase();

                for (JobPosting job : activeJobs) {
                    if (job.getStatus() != JobStatus.REJECTED && job.getStatus() != JobStatus.HIRED) {
                        String companyLower = job.getCompany().toLowerCase();
                        if (!companyLower.isEmpty()
                                && (from.contains(companyLower) || subject.contains(companyLower))) {

                            if (subject.contains("update") || subject.contains("status")
                                    || subject.contains("unfortunate") || subject.contains("rejection")) {
                                log.info("Found potential rejection/update email from {}", job.getCompany());

                                Object content = message.getContent();
                                String bodyText = content != null ? content.toString() : "";

                                if (bodyText.toLowerCase().contains("unfortunately")
                                        || subject.contains("unfortunately")
                                        || bodyText.toLowerCase().contains("other candidates")) {
                                    job.setStatus(JobStatus.REJECTED);
                                    jobPostingRepository.save(job);

                                    rejectionLearningProtocol.analyzeRejection(job, bodyText);
                                } else if (bodyText.toLowerCase().contains("interview") || subject.contains("interview")
                                        || bodyText.toLowerCase().contains("next steps")) {
                                    job.setStatus(JobStatus.INTERVIEWING);
                                    jobPostingRepository.save(job);
                                }
                            }

                            message.setFlag(Flags.Flag.SEEN, true);
                            break;
                        }
                    }
                }
            }

            inbox.close(false);
            store.close();
            log.info("IMAP Sync completed. Processed {} unread messages.", messages.length);

        } catch (Exception e) {
            log.error("Failed to sync IMAP emails: ", e);
        }
    }
}
