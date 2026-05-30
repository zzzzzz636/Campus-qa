package com.campusqa.controller;

import java.util.List;

import com.campusqa.model.Faq;
import com.campusqa.service.FaqService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping("/list")
    public List<Faq> list() {
        return faqService.listAll();
    }
}
