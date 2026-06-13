package com.campusqa.dto;

import java.util.List;

public record CrawlerRunResult(
        int successCount,
        int failCount,
        List<CrawlerPageResult> items
) {
}
