package com.campusqa.dto;

import java.util.List;

public record QaSearchResponse(
        String question,
        String matchedQuestion,
        String answer,
        String category,
        String source,
        List<String> relatedQuestions
) {
}

