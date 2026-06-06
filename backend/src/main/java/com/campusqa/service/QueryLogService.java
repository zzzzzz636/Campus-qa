package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.model.QueryLog;
import com.campusqa.repository.QueryLogRepository;
import org.springframework.stereotype.Service;

@Service
public class QueryLogService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final QueryLogRepository queryLogRepository;

    public QueryLogService(QueryLogRepository queryLogRepository) {
        this.queryLogRepository = queryLogRepository;
    }

    public ApiResponse<List<QueryLog>> list(Integer limit) {
        return ApiResponse.success("查询成功", queryLogRepository.findRecent(normalizeLimit(limit)));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
