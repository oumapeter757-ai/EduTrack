package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.entity.*;
import com.peterscode.edutrack.enrollment.repository.PromotionDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionClaimService {
    private final PromotionDecisionRepository decisions;
    private static final String WORKER_ID = "promotion-" + UUID.randomUUID();

    @Transactional
    public Optional<Long> claimOne(Long runId) {
        LocalDateTime now = LocalDateTime.now();
        return decisions.findClaimable(runId, PromotionDecisionStatus.PENDING, now, PageRequest.of(0, 1))
                .stream()
                .filter(d -> decisions.claim(d.getId(), PromotionDecisionStatus.PENDING, now,
                        WORKER_ID, now.plusMinutes(5)) == 1)
                .map(PromotionDecision::getId)
                .findFirst();
    }
}
