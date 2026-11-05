package com.peterscode.edutrack.enrollment.repository;

import com.peterscode.edutrack.enrollment.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

@Repository
public interface PromotionDecisionRepository extends JpaRepository<PromotionDecision, Long> {
    Page<PromotionDecision> findByRunId(Long runId, Pageable pageable);
    List<PromotionDecision> findByRunId(Long runId);
    long countByRunIdAndStatus(Long runId, PromotionDecisionStatus status);
    Optional<PromotionDecision> findByRunIdAndId(Long runId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PromotionDecision d where d.runId = :runId and d.id = :decisionId")
    Optional<PromotionDecision> findLockedByRunIdAndId(@org.springframework.data.repository.query.Param("runId") Long runId,
                                                        @org.springframework.data.repository.query.Param("decisionId") Long decisionId);

    @Query("select d from PromotionDecision d where d.runId = :runId and d.status = :status " +
            "and (d.claimedUntil is null or d.claimedUntil < :now) order by d.id")
    List<PromotionDecision> findClaimable(@org.springframework.data.repository.query.Param("runId") Long runId,
                                           @org.springframework.data.repository.query.Param("status") PromotionDecisionStatus status,
                                           @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
                                           org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("update PromotionDecision d set d.claimedBy = :worker, d.claimedUntil = :lease " +
            "where d.id = :id and d.status = :status and (d.claimedUntil is null or d.claimedUntil < :now)")
    int claim(@org.springframework.data.repository.query.Param("id") Long id,
              @org.springframework.data.repository.query.Param("status") PromotionDecisionStatus status,
              @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
              @org.springframework.data.repository.query.Param("worker") String worker,
              @org.springframework.data.repository.query.Param("lease") java.time.LocalDateTime lease);
}
