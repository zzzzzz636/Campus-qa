package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.ImportResult;
import com.campusqa.model.ImportHistory;
import com.campusqa.service.ImportService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/batch-faq")
    public ApiResponse<ImportResult> batchFaq(@RequestBody(required = false) JsonNode payload) {
        return importService.importBatchFaq(payload);
    }

    @PostMapping("/faq-csv")
    public ApiResponse<ImportResult> faqCsv(@RequestParam("file") MultipartFile file) {
        return importService.importFaqCsv(file);
    }

    @GetMapping("/history")
    public ApiResponse<List<ImportHistory>> history() {
        return importService.listHistory();
    }
}
