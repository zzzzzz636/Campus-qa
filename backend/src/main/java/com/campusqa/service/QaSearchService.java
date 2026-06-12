package com.campusqa.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.QaSearchRequest;
import com.campusqa.dto.QaSearchResult;
import com.campusqa.model.Faq;
import com.campusqa.model.KnowledgeDoc;
import com.campusqa.repository.FaqRepository;
import com.campusqa.repository.KnowledgeRepository;
import com.campusqa.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QaSearchService {

    private static final int DEFAULT_HOT_LIMIT = 6;
    private static final int MAX_HOT_LIMIT = 50;
    private static final int KNOWLEDGE_SUMMARY_LENGTH = 260;
    private static final Pattern KEYWORD_SPLIT_PATTERN = Pattern.compile("[\\s,，。?？!！、;；:：]+");

    private final FaqRepository faqRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final QueryLogRepository queryLogRepository;

    public QaSearchService(
            FaqRepository faqRepository,
            KnowledgeRepository knowledgeRepository,
            QueryLogRepository queryLogRepository
    ) {
        this.faqRepository = faqRepository;
        this.knowledgeRepository = knowledgeRepository;
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
            queryLogRepository.save("", "NONE", null, null, 0);
            return new QaSearchResult(
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    "NONE",
                    0,
                    "请输入问题"
            );
        }

        String normalized = question.trim();
        List<String> keywordParts = keywordParts(normalized);

        ScoredFaq bestFaq = faqRepository.findAll().stream()
                .map(faq -> new ScoredFaq(faq, scoreFaq(faq, normalized, keywordParts)))
                .filter(item -> item.matchScore() > 0)
                .max((left, right) -> compareFaqScore(left, right))
                .orElse(null);
        if (bestFaq != null) {
            return foundResult(normalized, bestFaq.faq(), bestFaq.matchScore());
        }

        return knowledgeRepository.findAll(null, null).stream()
                .map(doc -> new ScoredKnowledgeDoc(doc, scoreKnowledgeDoc(doc, normalized, keywordParts)))
                .filter(item -> item.matchScore() > 0)
                .max((left, right) -> compareKnowledgeScore(left, right))
                .map(item -> knowledgeResult(normalized, item.doc(), item.matchScore()))
                .orElseGet(() -> notFoundResult(normalized));
    }

    public ApiResponse<List<Faq>> hot(Integer limit) {
        return ApiResponse.success("查询成功", faqRepository.findHot(normalizeHotLimit(limit)));
    }

    private QaSearchResult foundResult(String input, Faq faq, int matchScore) {
        faqRepository.incrementViewCount(faq.id());
        queryLogRepository.save(input, "FAQ", faq.id(), null, matchScore);

        int updatedViewCount = faq.viewCount() == null ? 1 : faq.viewCount() + 1;
        return new QaSearchResult(
                input,
                faq.question(),
                faq.answer(),
                faq.category(),
                faq.source(),
                null,
                updatedViewCount,
                true,
                "FAQ",
                matchScore,
                "查询成功"
        );
    }

    private QaSearchResult knowledgeResult(String input, KnowledgeDoc doc, int matchScore) {
        queryLogRepository.save(input, "KNOWLEDGE_DOC", null, doc.id(), matchScore);

        String source = StringUtils.hasText(doc.sourceUrl()) ? doc.sourceUrl() : doc.sourceType();
        return new QaSearchResult(
                input,
                doc.title(),
                summarize(doc.content()),
                doc.category(),
                source,
                doc.sourceUrl(),
                0,
                true,
                "KNOWLEDGE_DOC",
                matchScore,
                "FAQ 未命中，已从知识资料库找到相关资料"
        );
    }

    private QaSearchResult notFoundResult(String input) {
        queryLogRepository.save(input, "NONE", null, null, 0);
        return new QaSearchResult(
                input,
                null,
                "暂未找到相关答案，请尝试换个关键词或提交问答建议。",
                null,
                null,
                null,
                0,
                false,
                "NONE",
                0,
                "暂未找到相关答案，请尝试换个关键词或提交问答建议。"
        );
    }

    private int scoreFaq(Faq faq, String keyword, List<String> keywordParts) {
        int score = 0;
        if (containsKeyword(faq.question(), keyword)) {
            score += 10;
        } else if (containsAnyKeywordPart(faq.question(), keywordParts)) {
            score += 6;
        }
        if (containsKeywordOrPart(faq.answer(), keyword, keywordParts)) {
            score += 3;
        }
        if (containsKeywordOrPart(faq.category(), keyword, keywordParts)) {
            score += 2;
        }
        if (containsKeywordOrPart(faq.source(), keyword, keywordParts)) {
            score += 1;
        }
        return score;
    }

    private int scoreKnowledgeDoc(KnowledgeDoc doc, String keyword, List<String> keywordParts) {
        int score = 0;
        if (containsKeyword(doc.title(), keyword)) {
            score += 10;
        } else if (containsAnyKeywordPart(doc.title(), keywordParts)) {
            score += 6;
        }
        if (containsKeywordOrPart(doc.content(), keyword, keywordParts)) {
            score += 3;
        }
        if (containsKeywordOrPart(doc.category(), keyword, keywordParts)) {
            score += 2;
        }
        if (containsKeywordOrPart(doc.sourceUrl(), keyword, keywordParts)
                || containsKeywordOrPart(doc.sourceType(), keyword, keywordParts)) {
            score += 1;
        }
        return score;
    }

    private int compareFaqScore(ScoredFaq left, ScoredFaq right) {
        int scoreCompare = Integer.compare(left.matchScore(), right.matchScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int viewCompare = Integer.compare(
                left.faq().viewCount() == null ? 0 : left.faq().viewCount(),
                right.faq().viewCount() == null ? 0 : right.faq().viewCount()
        );
        if (viewCompare != 0) {
            return viewCompare;
        }
        return Long.compare(left.faq().id(), right.faq().id());
    }

    private int compareKnowledgeScore(ScoredKnowledgeDoc left, ScoredKnowledgeDoc right) {
        int scoreCompare = Integer.compare(left.matchScore(), right.matchScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return Long.compare(left.doc().id(), right.doc().id());
    }

    private List<String> keywordParts(String keyword) {
        String normalized = normalizeText(keyword);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String part : KEYWORD_SPLIT_PATTERN.split(normalized)) {
            if (StringUtils.hasText(part)) {
                parts.add(part);
            }
        }
        if (normalized.length() > 2) {
            for (int i = 0; i <= normalized.length() - 2; i++) {
                String part = normalized.substring(i, i + 2);
                if (StringUtils.hasText(part)) {
                    parts.add(part);
                }
            }
        }
        return List.copyOf(parts);
    }

    private boolean containsKeywordOrPart(String value, String keyword, List<String> keywordParts) {
        return containsKeyword(value, keyword) || containsAnyKeywordPart(value, keywordParts);
    }

    private boolean containsKeyword(String value, String keyword) {
        return StringUtils.hasText(value) && normalizeText(value).contains(normalizeText(keyword));
    }

    private boolean containsAnyKeywordPart(String value, List<String> keywordParts) {
        if (!StringUtils.hasText(value) || keywordParts.isEmpty()) {
            return false;
        }
        String normalizedValue = normalizeText(value);
        return keywordParts.stream().anyMatch(normalizedValue::contains);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String summarize(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= KNOWLEDGE_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, KNOWLEDGE_SUMMARY_LENGTH) + "...";
    }

    private int normalizeHotLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_HOT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_HOT_LIMIT));
    }

    private record ScoredFaq(Faq faq, int matchScore) {
    }

    private record ScoredKnowledgeDoc(KnowledgeDoc doc, int matchScore) {
    }
}
