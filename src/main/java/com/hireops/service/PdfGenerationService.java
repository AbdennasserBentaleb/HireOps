package com.hireops.service;

import com.hireops.event.ApplicationScoredEvent;
import com.hireops.model.Application;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles post-scoring PDF finalization.
 *
 * NOTE: OllamaMatchmakerService already writes the cover letter PDF directly.
 *       This service's event listener MUST NOT re-generate the PDF (that caused
 *       the bug where the file path was rendered as the letter content).
 *
 *       The event listener is retained as a hook for future async post-processing
 *       (e.g. email sending, analytics) but no longer touches PDF content.
 */
@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);

    private final String pdfStoragePath;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public PdfGenerationService(
            @Value("${app.settings.pdf-storage-path:/app/data/pdfs}") String pdfStoragePath) {
        this.pdfStoragePath = pdfStoragePath;
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    /**
     * Called after an AI match is scored.
     *
     * IMPORTANT: OllamaMatchmakerService already wrote the cover letter PDF and
     * saved the correct path to Application.coverLetterPdfPath before publishing
     * this event. We must NOT overwrite it here.
     *
     * This listener only logs and can be used for future side-effects.
     */
    @EventListener
    public void onApplicationScored(ApplicationScoredEvent event) {
        Application app = event.getApplication();
        log.info("ApplicationScoredEvent received for application {} — cover letter at: {}",
                app.getId(), app.getCoverLetterPdfPath());
        // PDF is already generated and paths are already saved.
        // No action needed here.
    }

    /**
     * Utility: generate a PDF from markdown and write to a path.
     * Call this directly when you need to generate a standalone PDF.
     */
    public java.io.File generatePdfFromMarkdown(String markdown, Path destination) throws Exception {
        if (markdown == null || markdown.isBlank()) {
            markdown = "_No content provided._";
        }

        String htmlBody = renderer.render(parser.parse(markdown));
        String fullHtml = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: sans-serif; font-size: 14px; line-height: 1.6; color: #333; margin: 40px; }" +
                "h1, h2, h3 { color: #1e293b; }" +
                "a { color: #2563eb; text-decoration: none; }" +
                "</style></head><body>" + htmlBody + "</body></html>";

        Path dir = destination.getParent();
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        try (OutputStream os = new FileOutputStream(destination.toFile())) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(fullHtml, destination.toUri().toString());
            builder.toStream(os);
            builder.run();
        }
        return destination.toFile();
    }
}
