package com.campusqa.service;

import java.util.List;
import java.util.Locale;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.KnowledgeCleanupResult;
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

    @Transactional
    public ApiResponse<KnowledgeCleanupResult> cleanupDirty() {
        int deletedCount = 0;
        for (KnowledgeDoc doc : knowledgeRepository.findAll(null, null)) {
            if (isDirtyCrawlerDoc(doc) && knowledgeRepository.deleteById(doc.id()) > 0) {
                deletedCount++;
            }
        }

        return ApiResponse.success("清理完成", new KnowledgeCleanupResult(deletedCount));
    }

    private boolean isDirtyCrawlerDoc(KnowledgeDoc doc) {
        String title = normalizeText(doc.title());
        String content = normalizeText(doc.content());
        String sourceType = normalizeText(doc.sourceType()).toLowerCase(Locale.ROOT);
        boolean crawlerSource = sourceType.contains("爬虫") || sourceType.contains("crawler");

        if (!StringUtils.hasText(content)) {
            return crawlerSource;
        }
        if (content.contains("data-focus-url")
                || content.contains("校报 微信 微博")
                || content.contains("华工主页 导航")) {
            return true;
        }
        if ("华南理工大学新闻网".equals(title) && looksLikeNewsList(content)) {
            return true;
        }
        if (crawlerSource && hasCrawlerSidebarNoise(content)) {
            return true;
        }
        if (crawlerSource && isLowQualityContent(content)) {
            return true;
        }
        return false;
    }

    private boolean looksLikeNewsList(String content) {
        int dateCount = countMatches(content, "2026-") + countMatches(content, "2025-")
                + countMatches(content, "06 ") + countMatches(content, "05 ");
        int listKeywordCount = countMatches(content, "查看更多") + countMatches(content, "校园新闻")
                + countMatches(content, "媒体华园") + countMatches(content, "专题热点")
                + countMatches(content, "新媒体说");
        return dateCount >= 8 || listKeywordCount >= 3;
    }

    private boolean isLowQualityContent(String content) {
        int chineseCount = countChineseCharacters(content);
        if (content.length() < 120 || chineseCount < 80) {
            return true;
        }

        int noiseCount = countMatches(content, "首页") + countMatches(content, "查看更多")
                + countMatches(content, "导航") + countMatches(content, "版权所有")
                + countMatches(content, "ICP备") + countMatches(content, "微信公众号");
        return noiseCount >= 5 && chineseCount < 300;
    }

    private boolean hasCrawlerSidebarNoise(String content) {
        int locationIndex = content.indexOf("当前位置：");
        String head = content.substring(0, Math.min(content.length(), locationIndex >= 0 ? locationIndex : 500));
        int sidebarCount = countMatches(head, "最新发布") + countMatches(head, "专题热点")
                + countMatches(head, "理论学习") + countMatches(head, "追 梦 人")
                + countMatches(head, "新媒体说") + countMatches(head, "精彩视频");
        return locationIndex > 200 && sidebarCount >= 2;
    }

    private int countMatches(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return 0;
        }
        int count = 0;
        int index = value.indexOf(keyword);
        while (index >= 0) {
            count++;
            index = value.indexOf(keyword, index + keyword.length());
        }
        return count;
    }

    private int countChineseCharacters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                count++;
            }
        }
        return count;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
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
