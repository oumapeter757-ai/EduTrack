package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.entity.*;
import com.peterscode.edutrack.enrollment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import com.peterscode.edutrack.enrollment.event.PromotionRunCompletedEvent;
import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionWorker {
    private static final int BATCH_SIZE = 100;

    private final PromotionRunRepository runs;
    private final PromotionDecisionRepository decisions;
    private final PromotionExecutor executor;
    private final PromotionClaimService claimService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${edutrack.enrollment.worker-delay-ms:5000}")
    public void process() {
        runs.findAll(PageRequest.of(0, BATCH_SIZE)).stream()
                .filter(run -> run.getStatus() == PromotionRunStatus.RUNNING)
                .limit(1)
                .forEach(run -> processRun(run.getId()));
    }

    public void processRun(Long runId) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            Optional<Long> decisionId = claimService.claimOne(runId);
            if (decisionId.isEmpty()) {
                runs.findById(runId).ifPresent(run -> {
                    if (decisions.countByRunIdAndStatus(runId, PromotionDecisionStatus.PENDING) == 0) {
                        run.setStatus(PromotionRunStatus.COMPLETED);
                        run.setCompletedAt(LocalDateTime.now(clock));
                        run.setOpenMarker(null);
                        runs.save(run);
                        events.publishEvent(new PromotionRunCompletedEvent(run.getSchoolId(), run.getId()));
                    }
                });
                return;
            }
            try {
                executor.apply(runId, decisionId.get());
            } catch (RuntimeException ex) {
                log.error("Promotion decision {} failed", decisionId.get(), ex);
            }
        }
    }
}
