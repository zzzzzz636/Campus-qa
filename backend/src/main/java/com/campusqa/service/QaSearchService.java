package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.QaSearchRequest;
import com.campusqa.dto.QaSearchResult;
import com.campusqa.model.Faq;
import com.campusqa.repository.FaqRepository;
import com.campusqa.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QaSearchService {

    private static final int DEFAULT_HOT_LIMIT = 6;
    private static final int MAX_HOT_LIMIT = 50;

    private final FaqRepository faqRepository;
    private final QueryLogRepository queryLogRepository;

    public QaSearchService(FaqRepository faqRepository, QueryLogRepository queryLogRepository) {
        this.faqRepository = faqRepository;
        this.queryLogRepository = queryLogRepository;
    }

    @Transactional
    public QaSearchResult search(QaSearchRequest request) {
        String question = request == null ? "" : request.question();
        return search(question);
    }

    @Transactional
    public QaSearchResult search(String question) {
        if (!StringUtils.hasText(question)) {
            queryLogRepository.save("", null);
            return new QaSearchResult(
                    "",
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    "请输入问题"
            );
        }

        String normalized = question.trim();

        return faqRepository.search(normalized).stream()
                .findFirst()
                .map(faq -> foundResult(normalized, faq))
                .orElseGet(() -> notFoundResult(normalized));
    }

    public ApiResponse<List<Faq>> hot(Integer limit) {
        return ApiResponse.success("查询成功", faqRepository.findHot(normalizeHotLimit(limit)));
    }

    private QaSearchResult foundResult(String input, Faq faq) {
        faqRepository.incrementViewCount(faq.id());
        queryLogRepository.save(input, faq.id());

        int updatedViewCount = faq.viewCount() == null ? 1 : faq.viewCount() + 1;
        return new QaSearchResult(
                input,
                faq.question(),
                faq.answer(),
                faq.category(),
                faq.source(),
                updatedViewCount,
                true,
                "查询成功"
        );
    }

    private QaSearchResult notFoundResult(String input) {
        queryLogRepository.save(input, null);
        return new QaSearchResult(
                input,
                null,
                "暂未找到相关答案，请尝试换个关键词或提交问答建议。",
                null,
                null,
                0,
                false,
                "暂未找到相关答案，请尝试换个关键词或提交问答建议。"
        );
    }

    private int normalizeHotLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_HOT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_HOT_LIMIT));
    }
}
