package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.dto.PromotionRequests.CreateRun;
import com.peterscode.edutrack.enrollment.dto.PromotionRequests.OverrideBatch;
import com.peterscode.edutrack.enrollment.entity.PromotionDecision;
import com.peterscode.edutrack.enrollment.entity.PromotionRun;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface PromotionRunService {
    PromotionRun create(CreateRun request, UserPrincipal principal);
    PromotionRun preview(Long runId, Long schoolId, UserPrincipal principal);
    PromotionRun get(Long runId, Long schoolId, UserPrincipal principal);
    PageResponse<PromotionRun> list(Long schoolId, Pageable pageable, UserPrincipal principal);
    PageResponse<PromotionDecision> decisions(Long runId, Long schoolId, Pageable pageable, UserPrincipal principal);
    void override(Long runId, Long schoolId, OverrideBatch batch, UserPrincipal principal);
    PromotionRun approve(Long runId, Long schoolId, UserPrincipal principal);
    PromotionRun cancelApproval(Long runId, Long schoolId, UserPrincipal principal);
    void execute(Long runId, Long schoolId, UserPrincipal principal);
    PromotionRun progress(Long runId, Long schoolId, UserPrincipal principal);
    PromotionRun revert(Long runId, Long schoolId, String reason, UserPrincipal principal);
}
