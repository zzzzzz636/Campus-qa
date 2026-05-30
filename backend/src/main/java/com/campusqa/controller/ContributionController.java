package com.campusqa.controller;

import com.campusqa.dto.ContributionAddRequest;
import com.campusqa.dto.ContributionAddResponse;
import com.campusqa.service.ContributionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contribution")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @PostMapping("/add")
    public ContributionAddResponse add(@RequestBody(required = false) ContributionAddRequest request) {
        return contributionService.add(request);
    }
}
