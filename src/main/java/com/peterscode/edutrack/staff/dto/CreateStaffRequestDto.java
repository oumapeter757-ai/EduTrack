package com.peterscode.edutrack.staff.dto;

import com.peterscode.edutrack.staff.enums.StaffStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class CreateStaffRequestDto {
  Long schoolId, userId;

  @NotBlank
  @Size(max = 50)
  String staffNumber;

  @NotBlank
  @Size(max = 100)
  String firstName, lastName;
  String middleName;
  @Email String email;
  String phone, gender;
  LocalDate dateOfBirth, joinDate;
  String department,
      designation,
      qualification,
      addressLine1,
      addressLine2,
      city,
      state,
      country,
      postalCode,
      profileImageUrl;
  StaffStatus status;
}
