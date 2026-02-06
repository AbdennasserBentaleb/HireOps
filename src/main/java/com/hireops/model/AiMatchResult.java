package com.hireops.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AiMatchResult(
        @JsonAlias( {
                "score", "matchScore", "match_score" }) int score,

        @JsonAlias({ "coverLetterMarkdown", "cover_letter_markdown", "coverLetter" }) String coverLetterMarkdown){
}
