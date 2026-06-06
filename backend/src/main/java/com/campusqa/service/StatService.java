package com.campusqa.service;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.StatOverview;
import com.campusqa.repository.ContributionRepository;
import com.campusqa.repository.FaqRepository;
import com.campusqa.repository.QueryLogRepository;
import org.springframework.stereotype.Service;

@Service
public class StatService {

    private static final String PENDING_STATUS = "PENDING";

    private final FaqRepository faqRepository;
    private final ContributionRepository contributionRepository;
    private final QueryLogRepository queryLogRepository;

    public StatService(
            FaqRepository faqRepository,
            ContributionRepository contributionRepository,
            QueryLogRepository queryLogRepository
    ) {
        this.faqRepository = faqRepository;
        this.contributionRepository = contributionRepository;
        this.queryLogRepository = queryLogRepository;
    }

    public ApiResponse<StatOverview> overview() {
        StatOverview overview = new StatOverview(
                faqRepository.countEnabled(),
                contributionRepository.countByStatus(PENDING_STATUS),
                queryLogRepository.countAll(),
                faqRepository.countHot()
        );
        return ApiResponse.success("查询成功", overview);
    }
}
