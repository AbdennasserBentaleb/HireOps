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
        // 1. Markdown to HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlBody = renderer.render(parser.parse(markdown));

        // Let's add standard HTML and CSS wrapper to make the PDF look like a formal
        // document
        String fullHtml = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 11pt; line-height: 1.6; margin: 40px; color: #333; }"
                +
                "h1, h2, h3 { color: #000; margin-bottom: 10px; }" +
                "p { margin-bottom: 15px; text-align: justify; }" +
                "</style></head><body>" + htmlBody + "</body></html>";

        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File outFile = new File(dir, fileName);

        // 2. HTML to PDF
        try (FileOutputStream os = new FileOutputStream(outFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(fullHtml, "file://");
            builder.toStream(os);
            builder.run();
        }

        return outFile.getAbsolutePath();
    }
}
