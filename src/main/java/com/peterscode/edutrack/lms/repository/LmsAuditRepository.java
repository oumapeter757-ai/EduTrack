package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.audit.LmsAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsAuditRepository extends JpaRepository<LmsAudit, Long> {
}
