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

    /**
     * 启动爬虫采集（批量，从 seed_urls.txt 读取 URL）
     *
     * @param limit    最多采集页面数（默认 10，最大 30）
     * @param maxDepth 链接跟踪深度（默认 2，最大 3）
     */
    @PostMapping("/run")
    public ApiResponse<CrawlerRunResult> run(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxDepth) {
        return crawlerService.run(limit, maxDepth);
    }

    /**
     * 采集单个指定 URL（管理员输入单个公开网页地址）
     *
     * @param url      指定公开 URL
     * @param maxDepth 链接跟踪深度（默认 2，最大 3）
     */
    @PostMapping("/run-one")
    public ApiResponse<CrawlerRunResult> runOne(
            @RequestParam String url,
            @RequestParam(required = false) Integer maxDepth) {
        return crawlerService.runOne(url, maxDepth);
    }
}
