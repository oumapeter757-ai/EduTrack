package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolResponseDto {
    private Long id;
    private String name;
    private String code;
    private String subdomain;
    private String phone;
    private String email;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}