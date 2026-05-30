package com.campusqa.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "校园生活百事通后端服务已启动",
                "status", "ok",
                "time", LocalDateTime.now().toString()
        );
    }
}

