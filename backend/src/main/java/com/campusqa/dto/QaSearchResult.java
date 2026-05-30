package com.campusqa.dto;

public record QaSearchResult(
        String input,
        String question,
        String answer,
        String category,
        String source,
        Integer viewCount,
        Boolean found,
        String message
) {
}

