package com.peterscode.edutrack.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SchoolEntitlementsCacheListener {
    private final ModuleAccessService access;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(SchoolEntitlementsEvicted event) { access.evict(event.schoolId()); }
}
