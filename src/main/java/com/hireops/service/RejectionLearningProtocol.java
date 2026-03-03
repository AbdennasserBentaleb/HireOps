package com.hireops.service;

import com.hireops.model.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RejectionLearningProtocol {

    private static final Logger log = LoggerFactory.getLogger(RejectionLearningProtocol.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RejectionLearningProtocol(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public void analyzeRejection(JobPosting job, String emailBody) {
        log.info("[EXPERT] Triggering Rejection Learning Protocol for {} at {}", job.getTitle(), job.getCompany());

        String prompt = "You are an expert tech recruiter analyzer. Read this rejection email and extract the primary reason for rejection. Keep it under 2 sentences.\n\n"
                + emailBody;

        try {
            String rejectionReason = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Extracted Reason: {}", rejectionReason);

            // Save to Vector DB so future applications can "learn" from this
            Document learningDoc = new Document(
                    "Rejection for " + job.getTitle() + " at " + job.getCompany() + ": " + rejectionReason,
                    Map.of(
                            "type", "rejection_learning",
                            "company", job.getCompany(),
                            "jobTitle", job.getTitle()));

            vectorStore.add(List.of(learningDoc));
            log.info("Successfully embedded rejection insight into pgvector RAG memory.");

        } catch (Exception e) {
            log.error("Failed to analyze rejection with AI: ", e);
        }
    }
}
