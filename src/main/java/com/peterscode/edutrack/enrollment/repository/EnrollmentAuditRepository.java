package com.peterscode.edutrack.enrollment.repository;

import com.peterscode.edutrack.enrollment.entity.EnrollmentAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentAuditRepository extends JpaRepository<EnrollmentAudit, Long> {
}
