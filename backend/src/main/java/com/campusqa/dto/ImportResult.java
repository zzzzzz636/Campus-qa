package com.campusqa.dto;

import java.util.List;

public record ImportResult(
        int successCount,
        int failCount,
        List<String> failReasons
) {
}
