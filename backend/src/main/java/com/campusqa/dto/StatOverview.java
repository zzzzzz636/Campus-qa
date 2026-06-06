package com.campusqa.dto;

public record StatOverview(
        int faqCount,
        int pendingContributionCount,
        int totalQueryCount,
        int hotQuestionCount
) {
}
