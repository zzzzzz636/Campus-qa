package com.campusqa.controller;

import com.campusqa.dto.AdminLoginRequest;
import com.campusqa.dto.ApiResponse;
import com.campusqa.service.AdminService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ApiResponse<Void> login(@RequestBody(required = false) AdminLoginRequest request) {
        return adminService.login(request);
    }
}
