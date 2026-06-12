package com.campusqa.dto;

public record ImportFaqItem(
        String question,
        String answer,
        String category,
        String source
) {
}
