package com.campusqa.model;

public record KnowledgeDoc(
        Long id,
        String title,
        String content,
        String category,
        String sourceUrl,
        String sourceType,
        Integer viewCount,
        String createdAt,
        String updatedAt
) {
}
