package com.peterscode.edutrack.school.mapper;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class SchoolMapper {

    public static School toEntity(CreateSchoolRequestDto requestDto) {
        if (requestDto == null) {
            return null;
        }

        SubscriptionTier tier = requestDto.getSubscriptionTier() != null
                ? requestDto.getSubscriptionTier()
                : SubscriptionTier.STANDARD;

        Set<ServiceModule> modules = requestDto.getEnabledModules() != null && !requestDto.getEnabledModules().isEmpty()
                ? new HashSet<>(requestDto.getEnabledModules())
                : new HashSet<>(ServiceModule.getDefaultModulesForTier(tier));

        return School.builder()
                .name(requestDto.getName())
                .code(requestDto.getCode().toUpperCase().trim())
                .subdomain(requestDto.getSubdomain().toLowerCase().trim())
                .phone(requestDto.getPhone())
                .email(requestDto.getEmail())
                .websiteUrl(requestDto.getWebsiteUrl())
                .logoUrl(requestDto.getLogoUrl())
                .addressLine1(requestDto.getAddressLine1())
                .addressLine2(requestDto.getAddressLine2())
                .city(requestDto.getCity())
                .state(requestDto.getState())
                .country(requestDto.getCountry())
                .postalCode(requestDto.getPostalCode())
                .timezone(requestDto.getTimezone() != null && !requestDto.getTimezone().isBlank() ? requestDto.getTimezone() : "UTC")
                .currency(requestDto.getCurrency() != null && !requestDto.getCurrency().isBlank() ? requestDto.getCurrency() : "USD")
                .subscriptionTier(tier)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(modules)
                .maxStudents(requestDto.getMaxStudents())
                .maxTeachers(requestDto.getMaxTeachers())
                .active(true)
                .build();
    }

    public SchoolResponseDto toDto(School school) {
        if (school == null) {
            return null;
        }

        return SchoolResponseDto.builder()
                .id(school.getId())
                .name(school.getName())
                .code(school.getCode())
                .subdomain(school.getSubdomain())
                .phone(school.getPhone())
                .email(school.getEmail())
                .websiteUrl(school.getWebsiteUrl())
                .logoUrl(school.getLogoUrl())
                .addressLine1(school.getAddressLine1())
                .addressLine2(school.getAddressLine2())
                .city(school.getCity())
                .state(school.getState())
                .country(school.getCountry())
                .postalCode(school.getPostalCode())
                .address(school.getAddress())
                .timezone(school.getTimezone())
                .currency(school.getCurrency())
                .subscriptionTier(school.getSubscriptionTier())
                .subscriptionStatus(school.getSubscriptionStatus())
                .subscriptionPlan(school.getSubscriptionTier() != null ? school.getSubscriptionTier().name() : null)
                .subscriptionStartDate(school.getSubscriptionStartDate())
                .subscriptionEndDate(school.getSubscriptionEndDate())
                .enabledModules(school.getEnabledModules() != null ? new HashSet<>(school.getEnabledModules()) : Set.of())
                .maxStudents(school.getMaxStudents())
                .maxTeachers(school.getMaxTeachers())
                .active(school.isActive())
                .createdAt(school.getCreatedAt())
                .updatedAt(school.getUpdatedAt())
                .build();
    }

    public PublicSchoolProfileDto toPublicDto(School school) {
        if (school == null) {
            return null;
        }

        return PublicSchoolProfileDto.builder()
                .id(school.getId())
                .name(school.getName())
                .code(school.getCode())
                .subdomain(school.getSubdomain())
                .logoUrl(school.getLogoUrl())
                .websiteUrl(school.getWebsiteUrl())
                .timezone(school.getTimezone())
                .currency(school.getCurrency())
                .active(school.isActive())
                .build();
    }

    public static void updateEntityFromProfileDto(UpdateSchoolProfileRequestDto dto, School school) {
        if (dto == null || school == null) {
            return;
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            school.setName(dto.getName());
        }
        if (dto.getPhone() != null) {
            school.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            school.setEmail(dto.getEmail());
        }
        if (dto.getWebsiteUrl() != null) {
            school.setWebsiteUrl(dto.getWebsiteUrl());
        }
        if (dto.getLogoUrl() != null) {
            school.setLogoUrl(dto.getLogoUrl());
        }
        if (dto.getAddressLine1() != null) {
            school.setAddressLine1(dto.getAddressLine1());
        }
        if (dto.getAddressLine2() != null) {
            school.setAddressLine2(dto.getAddressLine2());
        }
        if (dto.getCity() != null) {
            school.setCity(dto.getCity());
        }
        if (dto.getState() != null) {
            school.setState(dto.getState());
        }
        if (dto.getCountry() != null) {
            school.setCountry(dto.getCountry());
        }
        if (dto.getPostalCode() != null) {
            school.setPostalCode(dto.getPostalCode());
        }
        if (dto.getTimezone() != null && !dto.getTimezone().isBlank()) {
            school.setTimezone(dto.getTimezone());
        }
        if (dto.getCurrency() != null && !dto.getCurrency().isBlank()) {
            school.setCurrency(dto.getCurrency());
        }
    }

    public static void updateEntityFromSubscriptionDto(UpdateSchoolSubscriptionRequestDto dto, School school) {
        if (dto == null || school == null) {
            return;
        }

        if (dto.getSubscriptionTier() != null) {
            school.setSubscriptionTier(dto.getSubscriptionTier());
        }
        if (dto.getSubscriptionStatus() != null) {
            school.setSubscriptionStatus(dto.getSubscriptionStatus());
        }
        if (dto.getSubscriptionStartDate() != null) {
            school.setSubscriptionStartDate(dto.getSubscriptionStartDate());
        }
        if (dto.getSubscriptionEndDate() != null) {
            school.setSubscriptionEndDate(dto.getSubscriptionEndDate());
        }
        if (dto.getEnabledModules() != null) {
            school.setEnabledModules(new HashSet<>(dto.getEnabledModules()));
        }
        if (dto.getMaxStudents() != null) {
            school.setMaxStudents(dto.getMaxStudents());
        }
        if (dto.getMaxTeachers() != null) {
            school.setMaxTeachers(dto.getMaxTeachers());
        }
    }

    public static void updateEntityFromDto(CreateSchoolRequestDto dto, School school) {
        if (dto == null || school == null) {
            return;
        }
        if (dto.getName() != null) school.setName(dto.getName());
        if (dto.getCode() != null) school.setCode(dto.getCode().toUpperCase().trim());
        if (dto.getSubdomain() != null) school.setSubdomain(dto.getSubdomain().toLowerCase().trim());
        if (dto.getPhone() != null) school.setPhone(dto.getPhone());
        if (dto.getEmail() != null) school.setEmail(dto.getEmail());
        if (dto.getWebsiteUrl() != null) school.setWebsiteUrl(dto.getWebsiteUrl());
        if (dto.getLogoUrl() != null) school.setLogoUrl(dto.getLogoUrl());
        if (dto.getAddressLine1() != null) school.setAddressLine1(dto.getAddressLine1());
        if (dto.getAddressLine2() != null) school.setAddressLine2(dto.getAddressLine2());
        if (dto.getCity() != null) school.setCity(dto.getCity());
        if (dto.getState() != null) school.setState(dto.getState());
        if (dto.getCountry() != null) school.setCountry(dto.getCountry());
        if (dto.getPostalCode() != null) school.setPostalCode(dto.getPostalCode());
        if (dto.getTimezone() != null) school.setTimezone(dto.getTimezone());
        if (dto.getCurrency() != null) school.setCurrency(dto.getCurrency());
        if (dto.getSubscriptionTier() != null) school.setSubscriptionTier(dto.getSubscriptionTier());
        if (dto.getEnabledModules() != null) school.setEnabledModules(new HashSet<>(dto.getEnabledModules()));
        if (dto.getMaxStudents() != null) school.setMaxStudents(dto.getMaxStudents());
        if (dto.getMaxTeachers() != null) school.setMaxTeachers(dto.getMaxTeachers());
    }
}
