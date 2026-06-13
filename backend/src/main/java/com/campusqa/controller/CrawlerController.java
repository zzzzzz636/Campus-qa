package com.campusqa.controller;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.CrawlerRunResult;
import com.campusqa.service.CrawlerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/run")
    public ApiResponse<CrawlerRunResult> run(@RequestParam(required = false) Integer limit) {
        return crawlerService.run(limit);
    }
}
