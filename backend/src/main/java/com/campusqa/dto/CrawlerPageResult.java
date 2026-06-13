package com.campusqa.dto;

public record CrawlerPageResult(
        String url,
        String title,
        Long knowledgeId,
        boolean success,
        String message
) {
}
