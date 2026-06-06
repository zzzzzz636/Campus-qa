package com.campusqa.controller;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.ContributionAddRequest;
import com.campusqa.dto.ContributionAddResponse;
import com.campusqa.model.Contribution;
import com.campusqa.service.ContributionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/pending")
    public List<Contribution> pending() {
        return contributionService.listPending();
    }

    @GetMapping("/list")
    public ApiResponse<List<Contribution>> list(@RequestParam(required = false) String status) {
        return contributionService.list(status);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<Contribution> detail(@PathVariable long id) {
        return contributionService.detail(id);
    }

    @PostMapping("/approve/{id}")
    public ApiResponse<Contribution> approve(@PathVariable long id) {
        return contributionService.approve(id);
    }

    @PostMapping("/reject/{id}")
    public ApiResponse<Contribution> reject(@PathVariable long id) {
        return contributionService.reject(id);
    }
}
