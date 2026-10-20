package com.peterscode.edutrack.staff.dto;

import com.peterscode.edutrack.staff.enums.StaffStatus;
import java.time.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponseDto {
  Long id, schoolId, userId;
  String staffNumber,
      firstName,
      lastName,
      middleName,
      fullName,
      email,
      phone,
      gender,
      department,
      designation,
      qualification,
      addressLine1,
      addressLine2,
      city,
      state,
      country,
      postalCode,
      profileImageUrl;
  LocalDate dateOfBirth, joinDate;
  StaffStatus status;
  boolean active;
  LocalDateTime createdAt, updatedAt;
}
