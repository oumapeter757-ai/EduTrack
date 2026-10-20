package com.peterscode.edutrack.guardian.dto;

import java.time.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuardianResponseDto {
  Long id, schoolId, userId;
  String firstName,
      lastName,
      fullName,
      email,
      phone,
      alternatePhone,
      relationship,
      occupation,
      addressLine1,
      addressLine2,
      city,
      state,
      country,
      postalCode;
  boolean active;
  LocalDateTime createdAt, updatedAt;
}
