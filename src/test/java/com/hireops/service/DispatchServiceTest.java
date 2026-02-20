package com.hireops.service;

import com.hireops.model.Application;
import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.ApplicationRepository;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private JobPostingRepository jobPostingRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private JavaMailSender javaMailSender;

    private DispatchService dispatchService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchService(jobPostingRepository, applicationRepository, userProfileRepository,
                javaMailSender);
    }

    @Test
    void testOnApplicationApproved_ShouldSendEmailAndUpdateStatus() throws IOException, MessagingException {
        UUID jobId = UUID.randomUUID();
        JobPosting job = new JobPosting();
        job.setId(jobId);
        job.setCompany("Tech Corp");
        job.setStatus(JobStatus.APPROVED);

        File cvFile = tempDir.resolve("cv.pdf").toFile();
        cvFile.createNewFile();

        File clFile = tempDir.resolve("cl.pdf").toFile();
        clFile.createNewFile();

        Application app = new Application();
        app.setJobPosting(job);
        app.setCvPdfPath(cvFile.getAbsolutePath());
        app.setCoverLetterPdfPath(clFile.getAbsolutePath());

        when(jobPostingRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(applicationRepository.findByJobPostingId(jobId)).thenReturn(List.of(app));

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        dispatchService.dispatchApplication(jobId, null);

        // Assert
        verify(javaMailSender).send(mimeMessage);
        verify(jobPostingRepository).save(job);

        assertThat(job.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(job.getEmployerEmail()).isEqualTo("hr@techcorp.de");
    }
}
