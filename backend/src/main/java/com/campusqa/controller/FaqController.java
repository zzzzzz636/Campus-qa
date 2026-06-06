package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.FaqSaveRequest;
import com.campusqa.model.Faq;
import com.campusqa.service.FaqService;
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
@RequestMapping("/api/faq")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Faq>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category
    ) {
        return faqService.list(keyword, category);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<Faq> detail(@PathVariable long id) {
        return faqService.detail(id);
    }

    @PostMapping("/add")
    public ApiResponse<Faq> add(@RequestBody(required = false) FaqSaveRequest request) {
        return faqService.add(request);
    }

    @PutMapping("/update")
    public ApiResponse<Faq> update(@RequestBody(required = false) FaqSaveRequest request) {
        return faqService.update(request);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        return faqService.delete(id);
    }
}
