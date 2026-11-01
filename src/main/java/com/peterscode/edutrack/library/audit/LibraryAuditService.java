package com.peterscode.edutrack.library.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LibraryAuditService {

    private final LibraryAuditRepository auditRepository;

    @Transactional
    public void append(Long schoolId, String entityType, Long entityId, String action,
                       Long actorId, String oldValue, String newValue, String reason) {
        LibraryAudit entry = LibraryAudit.builder()
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
