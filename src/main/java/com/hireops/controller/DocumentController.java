package com.hireops.controller;

import com.hireops.model.Application;
import com.hireops.repository.ApplicationRepository;
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
import java.util.UUID;

@Controller
@RequestMapping("/api/documents")
public class DocumentController {

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
            return ResponseEntity.notFound().build();
        }

        String filePath = isCv ? app.getCvPdfPath() : app.getCoverLetterPdfPath();
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"");

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
