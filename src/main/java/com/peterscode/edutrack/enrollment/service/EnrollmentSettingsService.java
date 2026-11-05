package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.entity.EnrollmentSettings;
import com.peterscode.edutrack.enrollment.repository.EnrollmentSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentSettingsService {
    private final EnrollmentSettingsRepository repository;

    @Transactional
    public EnrollmentSettings getOrCreate(Long schoolId) {
        return repository.findBySchoolId(schoolId).orElseGet(() -> repository.save(
                EnrollmentSettings.builder().schoolId(schoolId).build()));
    }

    @Transactional
    public EnrollmentSettings save(EnrollmentSettings settings) {
        if (settings.getSuggestedMinAverage() != null
                && (settings.getSuggestedMinAverage().signum() < 0
                || settings.getSuggestedMinAverage().compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Invalid enrollment settings");
        }
        if (settings.getUndoWindowDays() < 0 || settings.getUndoWindowDays() > 365
                || settings.getAlumniLoginDays() < 0 || settings.getAlumniLoginDays() > 3650
                || settings.getBatchSize() < 1 || settings.getBatchSize() > 500) {
            throw new IllegalArgumentException("Invalid enrollment settings");
        }
        return repository.save(settings);
    }
}
