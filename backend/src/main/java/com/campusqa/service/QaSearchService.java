package com.campusqa.service;

import java.util.List;

import com.campusqa.dto.QaSearchRequest;
import com.campusqa.dto.QaSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QaSearchService {

    public QaSearchResponse search(QaSearchRequest request) {
        String question = request == null ? "" : request.question();
        if (!StringUtils.hasText(question)) {
            return new QaSearchResponse(
                    "",
                    "暂未输入问题",
                    "请输入你想查询的校园生活问题。",
                    "系统提示",
                    "系统内置提示",
                    List.of()
            );
        }

        String normalized = question.trim();
        return new QaSearchResponse(
                normalized,
                "图书馆什么时候开放？",
                "图书馆通常在工作日和周末白天开放，具体时间以后续录入的学校图书馆公告为准。",
                "图书馆",
                "第一周模拟 FAQ 数据",
                List.of("图书馆怎么借书？", "图书馆座位需要预约吗？", "校园卡丢了怎么办？")
        );
    }
}

