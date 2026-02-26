package com.hireops.service;

import com.hireops.event.ApplicationScoredEvent;
import com.hireops.model.Application;
import com.hireops.repository.ApplicationRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.FileOutputStream;
import java.io.OutputStream;

@Service
public class PdfGenerationService {

    private final String pdfStoragePath;
    private final ApplicationRepository applicationRepository;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public PdfGenerationService(@Value("${app.settings.pdf-storage-path:/app/data/pdfs}") String pdfStoragePath,
            ApplicationRepository applicationRepository) {
        this.pdfStoragePath = pdfStoragePath;
        this.applicationRepository = applicationRepository;
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    @EventListener
    public void onApplicationScored(ApplicationScoredEvent event) {
        Application app = event.getApplication();
        String coverLetterMarkdown = app.getCoverLetterPdfPath(); // Temporarily held here by previous step

        try {
            ensureDirectoryExists();

            // Generate Cover Letter PDF
            String clFileName = String.format("cover_letter_%s.pdf", app.getId());
            File clFile = generatePdfFromMarkdown(coverLetterMarkdown, Paths.get(pdfStoragePath, clFileName));

            // Assume CV PDF is copied/generated similarly. For MVP, we generate a
            // placeholder CV PDF.
            String cvFileName = String.format("cv_%s.pdf", app.getId());
            File cvFile = generatePdfFromMarkdown(
                    "# John Doe CV\n\n**Senior Java Developer**\n\nHighly experienced in Spring Boot, AWS, and modern CI/CD.",
                    Paths.get(pdfStoragePath, cvFileName));

            app.setCoverLetterPdfPath(clFile.getAbsolutePath());
            app.setCvPdfPath(cvFile.getAbsolutePath());

            applicationRepository.save(app);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF documents", e);
        }
    }

    private void ensureDirectoryExists() throws IOException {
        Path path = Paths.get(pdfStoragePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    public File generatePdfFromMarkdown(String markdown, Path destination) throws Exception {
        String htmlBody = renderer.render(parser.parse(markdown));
        String fullHtml = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: sans-serif; font-size: 14px; line-height: 1.6; color: #333; margin: 40px; }" +
                "h1, h2, h3 { color: #1e293b; }" +
                "a { color: #2563eb; text-decoration: none; }" +
                "</style></head><body>" + htmlBody + "</body></html>";

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
