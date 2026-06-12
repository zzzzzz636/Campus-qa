package com.campusqa.dto;

public record QaSearchResult(
        String input,
        String question,
        String answer,
        String category,
        String source,
        String sourceUrl,
        Integer viewCount,
        Boolean found,
        String sourceType,
        Integer matchScore,
        String message
) {
}
