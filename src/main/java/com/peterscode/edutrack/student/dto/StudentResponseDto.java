package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponseDto {

    private Long id;
    private Long schoolId;
    private Long classLevelId;
    private Long streamId;
    private Long academicYearId;
    private Long userId;
    private String admissionNumber;
    private String firstName;
    private String lastName;
    private String middleName;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private BloodGroup bloodGroup;
    private String email;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String address;
    private String profileImageUrl;
    private LocalDate admissionDate;
    private String gradeLevel;
    private String section;
    private String rollNumber;
    private String academicYear;
    private StudentStatus status;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;
    private String guardianName;
    private String guardianEmail;
    private String guardianPhone;
    private String guardianRelationship;
    private String medicalNotes;
    private boolean active;
    private boolean enrolled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
