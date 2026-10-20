package com.peterscode.edutrack.student.audit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StudentAuditRepository extends JpaRepository<StudentAudit, Long> {}
