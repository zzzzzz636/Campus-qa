package com.campusqa.service;

import com.campusqa.dto.AdminLoginRequest;
import com.campusqa.dto.ApiResponse;
import com.campusqa.repository.AdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminService {

    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Transactional
    public ApiResponse<Void> login(AdminLoginRequest request) {
        if (request == null
                || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())) {
            return ApiResponse.failure("账号或密码错误");
        }

        String username = request.username().trim();
        String password = request.password().trim();

        return adminRepository.findByUsername(username)
                .filter(admin -> password.equals(admin.passwordHash()))
                .map(admin -> {
                    adminRepository.updateLastLoginAt(admin.id());
                    return ApiResponse.<Void>success("登录成功", null);
                })
                .orElseGet(() -> ApiResponse.failure("账号或密码错误"));
    }
}
