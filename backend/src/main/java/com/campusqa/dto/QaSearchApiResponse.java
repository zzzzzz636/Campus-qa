package com.campusqa.dto;

public record QaSearchApiResponse(
        boolean success,
        String message,
        QaSearchResult data,
        String input,
        String question,
        String answer,
        String category,
        String source,
        String sourceUrl,
        Integer viewCount,
        Boolean found,
        String sourceType,
        Integer matchScore
) {
    public static QaSearchApiResponse from(QaSearchResult result) {
        return new QaSearchApiResponse(
                true,
                result.message(),
                result,
                result.input(),
                result.question(),
                result.answer(),
                result.category(),
                result.source(),
                result.sourceUrl(),
                result.viewCount(),
                result.found(),
                result.sourceType(),
                result.matchScore()
        );
    }
}
