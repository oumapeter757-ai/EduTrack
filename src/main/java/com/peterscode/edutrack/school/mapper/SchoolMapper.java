package com.peterscode.edutrack.school.mapper;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.service.PlanCatalog;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Collection;
import java.time.ZoneId;

@Component
public class SchoolMapper {

    public School toEntity(CreateSchoolRequestDto requestDto) {
        if (requestDto == null) {
            return null;
        }

        SubscriptionTier tier = requestDto.getSubscriptionTier() == null
                ? SubscriptionTier.FREE : requestDto.getSubscriptionTier();

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
                .timezone(validTimezone(requestDto.getTimezone()))
                .currency(requestDto.getCurrency() != null && !requestDto.getCurrency().isBlank() ? requestDto.getCurrency() : "KES")
                .subscriptionTier(tier)
                .maxStudents(tier == SubscriptionTier.ENTERPRISE ? null : PlanCatalog.maxStudents(tier))
                .maxTeachers(tier == SubscriptionTier.ENTERPRISE ? null : PlanCatalog.maxTeachers(tier))
                .enabledModules(enumSet(ServiceModule.getDefaultModulesForTier(tier)))
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
                .enabledModules(school.getEnabledModules() != null
                        ? enumSet(school.getEnabledModules())
                        : EnumSet.noneOf(ServiceModule.class))
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
                .name(school.getName())
                .subdomain(school.getSubdomain())
                .logoUrl(school.getLogoUrl())
                .websiteUrl(school.getWebsiteUrl())
                .timezone(school.getTimezone())
                .currency(school.getCurrency())
                .active(school.isActive())
                .build();
    }

    public void updateEntityFromProfileDto(UpdateSchoolProfileRequestDto dto, School school) {
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
            school.setTimezone(validTimezone(dto.getTimezone()));
        }
        if (dto.getCurrency() != null && !dto.getCurrency().isBlank()) {
            school.setCurrency(dto.getCurrency());
        }
    }

    public void updateEntityFromSubscriptionDto(UpdateSchoolSubscriptionRequestDto dto, School school) {
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
            school.setEnabledModules(enumSet(dto.getEnabledModules()));
        }

        if (dto.getMaxStudents() != null) {
            school.setMaxStudents(dto.getMaxStudents());
        }
        if (dto.getMaxTeachers() != null) {
            school.setMaxTeachers(dto.getMaxTeachers());
        }
    }

    public void updateEntityFromDto(UpdateSchoolRequestDto dto, School school) {
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
        if (dto.getTimezone() != null) school.setTimezone(validTimezone(dto.getTimezone()));
        if (dto.getCurrency() != null) school.setCurrency(dto.getCurrency());
    }

    private static EnumSet<ServiceModule> enumSet(Collection<ServiceModule> modules) {
        EnumSet<ServiceModule> result = EnumSet.noneOf(ServiceModule.class);
        if (modules != null) result.addAll(modules);
        return result;
    }

    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) return "Africa/Nairobi";
        return ZoneId.of(timezone.trim()).getId();
    }
}
