package com.peterscode.edutrack.lms.audit;

import com.peterscode.edutrack.lms.repository.LmsAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LmsAuditService {

    private final LmsAuditRepository auditRepository;

    @Transactional
    public void append(Long schoolId, String entityType, Long entityId, String action,
                       Long actorId, String oldValue, String newValue, String reason) {
        LmsAudit entry = LmsAudit.builder()
                .schoolId(schoolId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        auditRepository.save(entry);
    }
}
