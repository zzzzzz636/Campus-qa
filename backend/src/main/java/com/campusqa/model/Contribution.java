package com.campusqa.model;

public record Contribution(
        Long id,
        String question,
        String suggestedAnswer,
        String category,
        String status,
        String createdAt
) {
}

