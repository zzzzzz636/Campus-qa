package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.ContributionAddRequest;
import com.campusqa.dto.ContributionAddResponse;
import com.campusqa.model.Contribution;
import com.campusqa.repository.ContributionRepository;
import com.campusqa.repository.FaqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ContributionService {

    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String DEFAULT_STATUS = "PENDING";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";

    private final ContributionRepository contributionRepository;
    private final FaqRepository faqRepository;

    public ContributionService(ContributionRepository contributionRepository, FaqRepository faqRepository) {
        this.contributionRepository = contributionRepository;
        this.faqRepository = faqRepository;
    }

    @Transactional
    public ContributionAddResponse add(ContributionAddRequest request) {
        String question = request == null ? null : request.question();
        if (!StringUtils.hasText(question)) {
            return new ContributionAddResponse(false, "问题不能为空", null);
        }

        String category = request.category();
        if (!StringUtils.hasText(category)) {
            category = DEFAULT_CATEGORY;
        }

        String suggestedAnswer = request.suggestedAnswer();
        Long id = contributionRepository.save(
                question.trim(),
                StringUtils.hasText(suggestedAnswer) ? suggestedAnswer.trim() : null,
                category.trim(),
                DEFAULT_STATUS
        );

        return new ContributionAddResponse(true, "提交成功，等待管理员审核", id);
    }

    public List<Contribution> listPending() {
        return contributionRepository.findByStatus(DEFAULT_STATUS);
    }

    public ApiResponse<List<Contribution>> list(String status) {
        String normalizedStatus = normalizeStatus(status);
        return ApiResponse.success("查询成功", contributionRepository.findAll(normalizedStatus));
    }

    public ApiResponse<Contribution> detail(long id) {
        return contributionRepository.findById(id)
                .map(contribution -> ApiResponse.success("查询成功", contribution))
                .orElseGet(() -> ApiResponse.failure("贡献记录不存在"));
    }

    @Transactional
    public ApiResponse<Contribution> approve(long id) {
        var contributionOptional = contributionRepository.findById(id);
        if (contributionOptional.isEmpty()) {
            return ApiResponse.failure("贡献记录不存在，无法通过");
        }

        Contribution contribution = contributionOptional.get();
        if (!DEFAULT_STATUS.equals(contribution.status())) {
            return ApiResponse.failure("只有待审核贡献可以通过");
        }
        if (!StringUtils.hasText(contribution.suggestedAnswer())) {
            return ApiResponse.failure("建议答案为空，请管理员补充答案后再通过");
        }

        faqRepository.save(
                contribution.question(),
                contribution.suggestedAnswer(),
                contribution.category(),
                "用户贡献"
        );
        contributionRepository.updateStatus(id, APPROVED_STATUS);

        return contributionRepository.findById(id)
                .map(updated -> ApiResponse.success("贡献已通过，并已加入 FAQ 知识库", updated))
                .orElseGet(() -> ApiResponse.failure("贡献状态更新失败"));
    }

    @Transactional
    public ApiResponse<Contribution> reject(long id) {
        var contributionOptional = contributionRepository.findById(id);
        if (contributionOptional.isEmpty()) {
            return ApiResponse.failure("贡献记录不存在，无法拒绝");
        }

        Contribution contribution = contributionOptional.get();
        if (!DEFAULT_STATUS.equals(contribution.status())) {
            return ApiResponse.failure("只有待审核贡献可以拒绝");
        }

        contributionRepository.updateStatus(id, REJECTED_STATUS);
        return contributionRepository.findById(id)
                .map(updated -> ApiResponse.success("贡献已拒绝", updated))
                .orElseGet(() -> ApiResponse.failure("贡献状态更新失败"));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim()) {
            case "待审核", "PENDING" -> DEFAULT_STATUS;
            case "已通过", "APPROVED" -> APPROVED_STATUS;
            case "已拒绝", "REJECTED" -> REJECTED_STATUS;
            default -> status.trim();
        };
    }
}
