package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchoolRequestDto {

   @NotBlank(message = "School name is required")
    @Size(min = 3, max = 255, message = "School name must be between 3 and 255 characters")
    private String name;

    @NotBlank(message = "School code is required")
    @Size(min = 2, max = 50, message = "School code must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "School code may only contain letters, numbers, hyphens, and underscores")
    private String code;

    @NotBlank(message = "Subdomain is required")
    @Size(min = 3, max = 100, message = "Subdomain must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$", message = "Subdomain must contain only lowercase letters, digits, and hyphens")
    private String subdomain;

    private String phone;

    @Email(message = "Must be a valid email address")
    private String email;

    private String websiteUrl;

    private String logoUrl;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String postalCode;

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private String currency = "USD";

    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;

}
