package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

/**
 * Cache-safe school profile DTO for Redis caching.
 * <p>
 * Excludes phone and email fields that are forbidden by P3 (no phone numbers
 * or emails of people in any cache). Contains only school metadata and
 * subscription configuration that is safe to cache.
 * <p>
 * The controller layer enriches this with phone/email from the database
 * when returning the full SchoolResponseDto to the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolProfileCacheDto {
    private Long id;
    private String name;
    private String code;
    private String subdomain;
    private String websiteUrl;
    private String logoUrl;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String address;
    private String timezone;
    private String currency;
    private SubscriptionTier subscriptionTier;
    private SubscriptionStatus subscriptionStatus;
    private String subscriptionPlan;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private Set<ServiceModule> enabledModules;
    private Integer maxStudents;
    private Integer maxTeachers;
    private boolean active;
}
