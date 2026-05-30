package com.campusqa.model;

public record QueryLog(
        Long id,
        String queryText,
        Long matchedFaqId,
        String createdAt
) {
}

