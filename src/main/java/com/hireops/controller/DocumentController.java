package com.hireops.controller;

import com.hireops.model.Application;
import com.hireops.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final ApplicationRepository applicationRepository;

    public DocumentController(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/cv/{jobId}")
    public ResponseEntity<Resource> getCv(@PathVariable UUID jobId) {
        return servePdfByJobId(jobId, true);
    }

    @GetMapping("/cl/{jobId}")
    public ResponseEntity<Resource> getCoverLetter(@PathVariable UUID jobId) {
        return servePdfByJobId(jobId, false);
    }

    private ResponseEntity<Resource> servePdfByJobId(UUID jobId, boolean isCv) {
        Application app = applicationRepository.findByJobPostingId(jobId).stream().findFirst().orElse(null);

        if (app == null) {
            log.warn("No Application record found for jobId={}", jobId);
            return htmlError("No application record found for this job.",
                    "The AI scoring step may not have completed yet, or state was lost after a pipeline clear.");
        }

        String filePath = isCv ? app.getCvPdfPath() : app.getCoverLetterPdfPath();

        if (filePath == null || filePath.isBlank()) {
            log.warn("No {} path stored for jobId={}", isCv ? "CV" : "cover letter", jobId);
            return htmlError(
                    isCv ? "No CV was linked to this application." : "No cover letter path stored.",
                    "The file path was not saved correctly. Try re-running AI Match on this job.");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("{} PDF not found on disk: path='{}', jobId={}", isCv ? "CV" : "Cover letter", filePath, jobId);
            return htmlError(
                    "PDF file not found on disk.",
                    "Expected location: <code>" + filePath + "</code><br><br>"
                    + "This usually means the Docker volume storing PDFs was not mounted correctly, "
                    + "or the container was recreated without the volume.<br><br>"
                    + "To recover: re-run <strong>AI Match</strong> on this job — a new PDF will be generated.");
        }

        Resource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"");

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    /** Returns an HTML page (served as text/html) that displays nicely inside the iframe. */
    private ResponseEntity<Resource> htmlError(String title, String detail) {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>body{font-family:Arial,sans-serif;display:flex;align-items:center;justify-content:center;"
                + "min-height:100vh;margin:0;background:#0f172a;color:#cbd5e1;}"
                + ".box{text-align:center;max-width:480px;padding:2rem;}"
                + "h2{color:#f87171;font-size:1rem;margin-bottom:0.75rem;}"
                + "p{font-size:0.85rem;line-height:1.6;color:#94a3b8;}"
                + "code{background:rgba(255,255,255,0.08);padding:0.1em 0.4em;border-radius:0.3em;"
                + "font-size:0.8rem;word-break:break-all;}"
                + "</style></head><body>"
                + "<div class='box'><h2>\u26a0\ufe0f " + title + "</h2><p>" + detail + "</p></div>"
                + "</body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Resource body = new org.springframework.core.io.ByteArrayResource(bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
