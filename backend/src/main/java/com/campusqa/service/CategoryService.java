package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.model.Category;
import com.campusqa.repository.CategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public ApiResponse<List<Category>> list() {
        return ApiResponse.success("查询成功", categoryRepository.findUsedByFaq());
    }
}
