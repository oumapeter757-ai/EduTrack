package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationSmsQuotaService {

    private final SchoolRepository schools;
    private final NotificationSmsQuotaProperties properties;

    public NotificationSmsQuotaService(SchoolRepository schools, NotificationSmsQuotaProperties properties) {
        this.schools = schools;
        this.properties = properties;
    }

    public int limitFor(long schoolId) {
        School school = schools.findById(schoolId)
                .orElseThrow(() -> new IllegalStateException("Notification school is unavailable"));
        SubscriptionTier tier = school.getSubscriptionTier();
        if (tier == null) {
            return 0;
        }
        int limit = properties.limitFor(tier);
        if (limit < 1 || limit > 10_000_000) {
            return 0;
        }
        return limit;
    }
}
