package com.hireops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the Retrieval-Augmented Generation (RAG)
 * memory bank.
 * It interacts with the underlying VectorStore (pgvector) to save knowledge and
 * retrieve context.
 */
@Service
public class RagMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RagMemoryService.class);

    private final VectorStore vectorStore;

    public RagMemoryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        log.info("RagMemoryService initialized with VectorStore: {}", vectorStore.getClass().getSimpleName());
    }

    /**
     * Stores a piece of knowledge into the RAG memory bank.
     * The input text will be automatically embedded by the configured
     * EmbeddingModel before storage.
     *
     * @param content  The raw text content to memorize.
     * @param metadata Optional metadata to associate with this memory (e.g.,
     *                 source, category, timestamp).
     */
    public void memorizeContext(String content, Map<String, Object> metadata) {
        log.info("Memorizing new context into Vector DB...");

        Document document = new Document(content, metadata != null ? metadata : Map.of());

        // The VectorStore handles calling the EmbeddingModel and persisting to
        // PostgreSQL (pgvector)
        vectorStore.add(List.of(document));

        log.info("Context successfully embedded and stored.");
    }

    /**
     * Retrieves relevant context from the memory bank based on semantic similarity
     * to the query.
     *
     * @param query The search string (e.g., a technical requirement from a Job
     *              Description).
     * @param topK  The maximum number of relevant documents to retrieve.
     * @return A list of context strings retrieved from the Vector DB.
     */
    public List<String> retrieveRelevantContext(String query, int topK) {
        log.debug("Searching Vector DB for context relevant to: '{}'", query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.debug("Found {} relevant documents.", results.size());

        return results.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    /**
     * Optional: Clear the memory bank (useful for testing or resetting state).
     */
    public void wipeMemory() {
        log.warn(
                "WARNING: WIPING ALL VECTOR STORE MEMORY (Not fully implemented by all stores natively without direct SQL).");
        // Note: Some stores require direct truncate. We will log for now.
    }
}
