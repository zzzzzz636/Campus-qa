package com.campusqa.controller;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.StatOverview;
import com.campusqa.service.StatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stat")
public class StatController {

    private final StatService statService;

    public StatController(StatService statService) {
        this.statService = statService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatOverview> overview() {
        return statService.overview();
    }
}
