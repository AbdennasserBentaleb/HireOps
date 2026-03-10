package com.hireops.service;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    /**
     * Extracts all raw text from an existing PDF file to feed to the LLM.
     */
    public String extractTextFromPdf(String pdfPath) throws IOException {
        String normalizedPath = pdfPath.replace('\\', '/');
        File file = new File(normalizedPath);
        if (!file.exists()) {
            throw new IOException("PDF file not found at: " + pdfPath);
        }

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Converts markdown text into a styled HTML string, then renders it into a PDF
     * file.
     * Returns the absolute path of the generated PDF.
     */
    public String generateCoverLetterPdf(String markdown, String storageDir, String fileName) throws Exception {
        // 1. Markdown → HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlBody = renderer.render(parser.parse(markdown));

        // Professional DIN-5008-style cover letter CSS
        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>" +
                "@page { margin: 2.5cm 2.5cm 2cm 2.5cm; }" +
                "body { font-family: 'Arial', 'Helvetica', sans-serif; font-size: 11pt; " +
                "       line-height: 1.65; color: #1a1a1a; margin: 0; padding: 0; }" +
                "h1 { font-size: 13pt; font-weight: bold; color: #1a1a1a; " +
                "     border-bottom: 1.5pt solid #1a1a1a; padding-bottom: 4pt; margin-bottom: 16pt; }" +
                "h2 { font-size: 11pt; font-weight: bold; color: #1a1a1a; margin-bottom: 6pt; }" +
                "p  { margin: 0 0 10pt 0; text-align: justify; orphans: 3; widows: 3; }" +
                "ul { margin: 0 0 10pt 0; padding-left: 18pt; }" +
                "li { margin-bottom: 4pt; }" +
                "strong { font-weight: bold; }" +
                "em { font-style: italic; }" +
                "</style></head><body>" + htmlBody + "</body></html>";

        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File outFile = new File(dir, fileName);

        // 2. HTML → PDF  (base URI must be the directory so relative refs resolve)
        try (FileOutputStream os = new FileOutputStream(outFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Use the actual directory URI as base so openhtmltopdf can resolve resources
            builder.withHtmlContent(fullHtml, dir.toURI().toString());
            builder.toStream(os);
            builder.run();
        }

        return outFile.getAbsolutePath();
    }

}
