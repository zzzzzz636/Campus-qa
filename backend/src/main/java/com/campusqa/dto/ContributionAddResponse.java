package com.campusqa.dto;

public record ContributionAddResponse(
        boolean success,
        String message,
        Long id
) {
}

