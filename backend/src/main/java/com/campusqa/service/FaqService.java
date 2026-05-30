package com.campusqa.service;

import java.util.List;

import com.campusqa.model.Faq;
import com.campusqa.repository.FaqRepository;
import org.springframework.stereotype.Service;

@Service
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    public List<Faq> listAll() {
        return faqRepository.findAll();
    }
}

