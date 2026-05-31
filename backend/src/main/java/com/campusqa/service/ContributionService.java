package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.ContributionAddRequest;
import com.campusqa.dto.ContributionAddResponse;
import com.campusqa.model.Contribution;
import com.campusqa.repository.ContributionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ContributionService {

    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String DEFAULT_STATUS = "待审核";

    private final ContributionRepository contributionRepository;

    public ContributionService(ContributionRepository contributionRepository) {
        this.contributionRepository = contributionRepository;
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
}
