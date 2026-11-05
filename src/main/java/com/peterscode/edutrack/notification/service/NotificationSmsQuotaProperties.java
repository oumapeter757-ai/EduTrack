package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "edutrack.notifications.sms-quota")
public class NotificationSmsQuotaProperties {

    private Map<SubscriptionTier, Integer> monthlySegments = new EnumMap<>(SubscriptionTier.class);

    public Map<SubscriptionTier, Integer> getMonthlySegments() {
        return monthlySegments;
    }

    public void setMonthlySegments(Map<SubscriptionTier, Integer> monthlySegments) {
        this.monthlySegments = monthlySegments == null ? new EnumMap<>(SubscriptionTier.class)
                : new EnumMap<>(monthlySegments);
    }

    public int limitFor(SubscriptionTier tier) {
        return monthlySegments.getOrDefault(tier, 0);
    }
}
