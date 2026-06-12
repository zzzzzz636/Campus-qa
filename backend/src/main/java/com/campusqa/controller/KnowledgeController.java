package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.KnowledgeDocSaveRequest;
import com.campusqa.model.KnowledgeDoc;
import com.campusqa.service.KnowledgeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/list")
    public ApiResponse<List<KnowledgeDoc>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category
    ) {
        return knowledgeService.list(keyword, category);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<KnowledgeDoc> detail(@PathVariable long id) {
        return knowledgeService.detail(id);
    }

    @PostMapping("/add")
    public ApiResponse<KnowledgeDoc> add(@RequestBody(required = false) KnowledgeDocSaveRequest request) {
        return knowledgeService.add(request);
    }

    @PutMapping("/update")
    public ApiResponse<KnowledgeDoc> update(@RequestBody(required = false) KnowledgeDocSaveRequest request) {
        return knowledgeService.update(request);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        return knowledgeService.delete(id);
    }
}
