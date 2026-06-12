package com.campusqa.dto;

public record KnowledgeDocSaveRequest(
        Long id,
        String title,
        String content,
        String category,
        String sourceUrl,
        String sourceType
) {
}
