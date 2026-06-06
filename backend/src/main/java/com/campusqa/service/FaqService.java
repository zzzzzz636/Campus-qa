package com.campusqa.service;

import java.util.List;
import java.util.Optional;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.FaqSaveRequest;
import com.campusqa.model.Faq;
import com.campusqa.repository.FaqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    public List<Faq> listAll() {
        return faqRepository.findAll();
    }

    public ApiResponse<List<Faq>> list(String keyword, String category) {
        return ApiResponse.success("查询成功", faqRepository.findAll(keyword, category));
    }

    public ApiResponse<Faq> detail(long id) {
        Optional<Faq> faq = faqRepository.findById(id);
        return faq.map(value -> ApiResponse.success("查询成功", value))
                .orElseGet(() -> ApiResponse.failure("FAQ 不存在"));
    }

    @Transactional
    public ApiResponse<Faq> add(FaqSaveRequest request) {
        String validationMessage = validateRequired(request, false);
        if (validationMessage != null) {
            return ApiResponse.failure(validationMessage);
        }

        Long id = faqRepository.save(
                request.question().trim(),
                request.answer().trim(),
                normalizedCategory(request.category()),
                normalizedOptional(request.source())
        );
        if (id == null) {
            return ApiResponse.failure("FAQ 新增失败");
        }

        return detail(id).success()
                ? ApiResponse.success("FAQ 新增成功", faqRepository.findById(id).orElse(null))
                : ApiResponse.failure("FAQ 新增失败");
    }

    @Transactional
    public ApiResponse<Faq> update(FaqSaveRequest request) {
        String validationMessage = validateRequired(request, true);
        if (validationMessage != null) {
            return ApiResponse.failure(validationMessage);
        }

        int rows = faqRepository.update(
                request.id(),
                request.question().trim(),
                request.answer().trim(),
                normalizedCategory(request.category()),
                normalizedOptional(request.source())
        );
        if (rows == 0) {
            return ApiResponse.failure("FAQ 不存在，无法修改");
        }

        return ApiResponse.success("FAQ 修改成功", faqRepository.findById(request.id()).orElse(null));
    }

    @Transactional
    public ApiResponse<Void> delete(long id) {
        int rows = faqRepository.deleteById(id);
        if (rows == 0) {
            return ApiResponse.failure("FAQ 不存在，无法删除");
        }
        return ApiResponse.success("FAQ 删除成功", null);
    }

    private String validateRequired(FaqSaveRequest request, boolean requireId) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (requireId && request.id() == null) {
            return "id 不能为空";
        }
        if (!StringUtils.hasText(request.question())) {
            return "问题不能为空";
        }
        if (!StringUtils.hasText(request.answer())) {
            return "答案不能为空";
        }
        return null;
    }

    private String normalizedCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : "未分类";
    }

    private String normalizedOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
