package com.campusqa.dto;

public record FaqSaveRequest(
        Long id,
        String question,
        String answer,
        String category,
        String source
) {
}

