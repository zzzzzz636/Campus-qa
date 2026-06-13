package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.KnowledgeDocSaveRequest;
import com.campusqa.model.KnowledgeDoc;
import com.campusqa.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeService {

    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String DEFAULT_SOURCE_TYPE = "MANUAL";

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    public ApiResponse<List<KnowledgeDoc>> list(String keyword, String category) {
        return ApiResponse.success("查询成功", knowledgeRepository.findAll(keyword, category));
    }

    public ApiResponse<KnowledgeDoc> detail(long id) {
        return knowledgeRepository.findById(id)
                .map(doc -> ApiResponse.success("查询成功", doc))
                .orElseGet(() -> ApiResponse.failure("知识资料不存在"));
    }

    @Transactional
    public ApiResponse<KnowledgeDoc> add(KnowledgeDocSaveRequest request) {
        String validationMessage = validateRequired(request);
        if (validationMessage != null) {
            return ApiResponse.failure(validationMessage);
        }

        Long id = knowledgeRepository.save(
                request.title().trim(),
                request.content().trim(),
                normalizeCategory(request.category()),
                normalizeOptional(request.sourceUrl()),
                normalizeSourceType(request.sourceType())
        );
        if (id == null) {
            return ApiResponse.failure("知识资料新增失败");
        }

        return knowledgeRepository.findById(id)
                .map(doc -> ApiResponse.success("知识资料新增成功", doc))
                .orElseGet(() -> ApiResponse.failure("知识资料新增失败"));
    }

    @Transactional
    public ApiResponse<KnowledgeDoc> update(KnowledgeDocSaveRequest request) {
        String validationMessage = validateRequired(request);
        if (validationMessage != null) {
            return ApiResponse.failure(validationMessage);
        }
        if (request.id() == null) {
            return ApiResponse.failure("知识资料 id 不能为空");
        }

        int rows = knowledgeRepository.update(
                request.id(),
                request.title().trim(),
                request.content().trim(),
                normalizeCategory(request.category()),
                normalizeOptional(request.sourceUrl()),
                normalizeSourceType(request.sourceType())
        );
        if (rows == 0) {
            return ApiResponse.failure("知识资料不存在，无法修改");
        }

        return knowledgeRepository.findById(request.id())
                .map(doc -> ApiResponse.success("知识资料修改成功", doc))
                .orElseGet(() -> ApiResponse.failure("知识资料修改失败"));
    }

    @Transactional
    public ApiResponse<Void> delete(long id) {
        int rows = knowledgeRepository.deleteById(id);
        if (rows == 0) {
            return ApiResponse.failure("知识资料不存在，无法删除");
        }
        return ApiResponse.success("知识资料删除成功", null);
    }

    /**
     * 重置知识资料库：清空所有资料及关联查询日志
     */
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> resetAll() {
        int countBefore = knowledgeRepository.countAll();
        int deleted = knowledgeRepository.resetAll();
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("deleted", deleted);
        info.put("remaining", countBefore - deleted);
        return ApiResponse.success("已清空 " + deleted + " 条知识资料，可以重新采集了", info);
    }

    /**
     * 清理低质量资料：正文少于指定长度的记录
     */
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> cleanup(int minContentLength) {
        int countBefore = knowledgeRepository.countAll();
        int deleted = knowledgeRepository.cleanupLowQuality(minContentLength);
        int countAfter = countBefore - deleted;
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("deleted", deleted);
        info.put("remaining", countAfter);
        return ApiResponse.success("已清理 " + deleted + " 条低质量资料，剩余 " + countAfter + " 条", info);
    }

    private String validateRequired(KnowledgeDocSaveRequest request) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (!StringUtils.hasText(request.title())) {
            return "标题不能为空";
        }
        if (!StringUtils.hasText(request.content())) {
            return "内容不能为空";
        }
        return null;
    }

    private String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : DEFAULT_CATEGORY;
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim() : DEFAULT_SOURCE_TYPE;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
