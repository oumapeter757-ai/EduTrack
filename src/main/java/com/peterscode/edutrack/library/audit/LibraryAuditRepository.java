package com.peterscode.edutrack.library.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LibraryAuditRepository extends JpaRepository<LibraryAudit, Long> {
}
