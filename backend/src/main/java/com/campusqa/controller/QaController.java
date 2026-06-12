package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.QaSearchApiResponse;
import com.campusqa.dto.QaSearchRequest;
import com.campusqa.model.Faq;
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
    public QaSearchApiResponse search(@RequestParam(required = false) String question) {
        return QaSearchApiResponse.from(qaSearchService.search(question));
    }

    @PostMapping("/search")
    public QaSearchApiResponse search(@RequestBody QaSearchRequest request) {
        return QaSearchApiResponse.from(qaSearchService.search(request));
    }

    @GetMapping("/hot")
    public ApiResponse<List<Faq>> hot(@RequestParam(required = false) Integer limit) {
        return qaSearchService.hot(limit);
    }
}
