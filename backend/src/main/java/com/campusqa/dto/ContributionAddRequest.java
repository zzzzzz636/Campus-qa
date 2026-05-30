package com.campusqa.dto;

public record ContributionAddRequest(
        String question,
        String suggestedAnswer,
        String category
) {
}

