package com.campusqa.model;

public record Faq(
        Long id,
        String question,
        String answer,
        String category,
        String source,
        Integer viewCount,
        String createdAt
) {
}

