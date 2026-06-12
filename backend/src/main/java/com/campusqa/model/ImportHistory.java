package com.campusqa.model;

public record ImportHistory(
        Long id,
        String fileName,
        String importType,
        Integer successCount,
        Integer failCount,
        String message,
        String createdAt
) {
}
