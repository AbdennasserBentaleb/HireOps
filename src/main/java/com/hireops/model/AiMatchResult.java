package com.hireops.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the structured JSON response from the Ollama LLM.
 * Resilient to extra fields the model may output.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMatchResult(
        @JsonAlias({ "score", "matchScore", "match_score", "compatibility_score", "rating" }) int score,

        @JsonAlias({ "coverLetterMarkdown", "cover_letter_markdown", "coverLetter", "cover_letter",
                "letter" }) String coverLetterMarkdown,

        @JsonAlias({ "analysis", "reasoning", "matchAnalysis", "match_analysis",
                "explanation" }) com.fasterxml.jackson.databind.JsonNode analysis) {
}
