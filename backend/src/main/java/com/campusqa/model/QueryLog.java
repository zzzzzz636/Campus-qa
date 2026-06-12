package com.campusqa.model;

public record QueryLog(
        Long id,
        String queryText,
        String matchedType,
        Long matchedFaqId,
        Long matchedKnowledgeId,
        Integer matchScore,
        String createdAt
) {
}
