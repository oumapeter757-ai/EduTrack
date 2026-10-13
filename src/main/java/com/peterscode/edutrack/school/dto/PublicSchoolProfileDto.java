package com.peterscode.edutrack.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Publicly viewable school profile for login screens and client verification.
 * Excludes sensitive subscription, limits, and internal billing details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSchoolProfileDto {
    private Long id;
    private String name;
    private String code;
    private String subdomain;
    private String logoUrl;
    private String websiteUrl;
    private String timezone;
    private String currency;
    private boolean active;
}
