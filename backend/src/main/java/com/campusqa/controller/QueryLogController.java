package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.model.QueryLog;
import com.campusqa.service.QueryLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query-log")
public class QueryLogController {

    private final QueryLogService queryLogService;

    public QueryLogController(QueryLogService queryLogService) {
        this.queryLogService = queryLogService;
    }

    @GetMapping("/list")
    public ApiResponse<List<QueryLog>> list(@RequestParam(required = false) Integer limit) {
        return queryLogService.list(limit);
    }
}
