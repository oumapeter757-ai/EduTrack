package com.peterscode.edutrack.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating general school profile information.
 * Code and Subdomain are immutable once created to preserve tenant routing and history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolProfileRequestDto {

    @NotBlank(message = "School name is required")
    @Size(min = 3, max = 255, message = "School name must be between 3 and 255 characters")
    private String name;

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

    private String timezone;

    private String currency;
}
