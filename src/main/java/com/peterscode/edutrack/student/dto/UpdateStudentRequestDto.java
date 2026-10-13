package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentRequestDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @Size(max = 100, message = "Middle name cannot exceed 100 characters")
    private String middleName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

    private BloodGroup bloodGroup;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @Size(max = 50, message = "Phone cannot exceed 50 characters")
    private String phone;

    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    private String addressLine2;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 500, message = "Profile image URL cannot exceed 500 characters")
    private String profileImageUrl;

    private LocalDate admissionDate;

    @Size(max = 50, message = "Grade level cannot exceed 50 characters")
    private String gradeLevel;

    @Size(max = 50, message = "Section cannot exceed 50 characters")
    private String section;

    @Size(max = 50, message = "Roll number cannot exceed 50 characters")
    private String rollNumber;

    @Size(max = 50, message = "Academic year cannot exceed 50 characters")
    private String academicYear;

    @Size(max = 150, message = "Emergency contact name cannot exceed 150 characters")
    private String emergencyContactName;

    @Size(max = 50, message = "Emergency contact phone cannot exceed 50 characters")
    private String emergencyContactPhone;

    @Size(max = 50, message = "Emergency contact relationship cannot exceed 50 characters")
    private String emergencyContactRelationship;

    @Size(max = 150, message = "Guardian name cannot exceed 150 characters")
    private String guardianName;

    @Email(message = "Invalid guardian email format")
    @Size(max = 255, message = "Guardian email cannot exceed 255 characters")
    private String guardianEmail;

    @Size(max = 50, message = "Guardian phone cannot exceed 50 characters")
    private String guardianPhone;

    @Size(max = 50, message = "Guardian relationship cannot exceed 50 characters")
    private String guardianRelationship;

    private String medicalNotes;
}
