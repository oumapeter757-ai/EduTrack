package com.peterscode.edutrack.enrollment.repository;

import com.peterscode.edutrack.enrollment.entity.EnrollmentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EnrollmentSettingsRepository extends JpaRepository<EnrollmentSettings, Long> {
    Optional<EnrollmentSettings> findBySchoolId(Long schoolId);
}
