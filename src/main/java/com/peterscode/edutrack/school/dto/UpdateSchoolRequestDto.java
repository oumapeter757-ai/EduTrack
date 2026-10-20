package com.peterscode.edutrack.school.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateSchoolRequestDto {

    @NotBlank
    @Size(min = 3, max = 255)
    private String name;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String code;

    @NotBlank
    @Size(min = 3, max = 100)
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$")
    private String subdomain;

    private String phone;

    @Email
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
