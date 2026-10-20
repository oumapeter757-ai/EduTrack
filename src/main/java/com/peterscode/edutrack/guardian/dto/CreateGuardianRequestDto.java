package com.peterscode.edutrack.guardian.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class CreateGuardianRequestDto {
  Long schoolId, userId;
  @NotBlank @Size(max = 100) String firstName, lastName;
  @Email @Size(max = 255) String email;
  @NotBlank @Size(max = 50) String phone;
  @Size(max = 50) String alternatePhone;
  @NotBlank @Size(max = 50) String relationship;
  @Size(max = 100) String occupation;
  @Size(max = 255) String addressLine1, addressLine2;
  @Size(max = 100) String city, state, country;
  @Size(max = 20) String postalCode;
}
