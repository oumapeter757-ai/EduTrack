package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSummaryDto {

    private Long id;
    private Long schoolId;
    private Long userId;
    private String admissionNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String gradeLevel;
    private String section;
    private String rollNumber;
    private Gender gender;
    private StudentStatus status;
    private String profileImageUrl;
    private boolean active;
}
