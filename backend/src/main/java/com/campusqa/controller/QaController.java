package com.campusqa.controller;

import com.campusqa.dto.QaSearchRequest;
import com.campusqa.dto.QaSearchResult;
import com.campusqa.service.QaSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaSearchService qaSearchService;

    public QaController(QaSearchService qaSearchService) {
        this.qaSearchService = qaSearchService;
    }

    @GetMapping("/search")
    public QaSearchResult search(@RequestParam(required = false) String question) {
        return qaSearchService.search(question);
    }

    @PostMapping("/search")
    public QaSearchResult search(@RequestBody QaSearchRequest request) {
        return qaSearchService.search(request);
    }
}
