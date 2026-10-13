package com.peterscode.edutrack.student.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(
        name = "students",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_school_admission", columnNames = {"school_id", "admission_number"})
        },
        indexes = {
                @Index(name = "idx_student_school_grade", columnList = "school_id, grade_level"),
                @Index(name = "idx_student_school_status", columnList = "school_id, status"),
                @Index(name = "idx_student_school_active", columnList = "school_id, active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Student extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "admission_number", nullable = false, length = 50)
    private String admissionNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 20)
    private BloodGroup bloodGroup;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address_line_1")
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    
    @Column(name = "grade_level", length = 50)
    private String gradeLevel;

    @Column(name = "section", length = 50)
    private String section;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    @Column(name = "academic_year", length = 50)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 50)
    private String emergencyContactRelationship;

    @Column(name = "guardian_name", length = 150)
    private String guardianName;

    @Column(name = "guardian_email")
    private String guardianEmail;

    @Column(name = "guardian_phone", length = 50)
    private String guardianPhone;

    @Column(name = "guardian_relationship", length = 50)
    private String guardianRelationship;

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Returns full formatted name.
     */
    public String getFullName() {
        if (middleName != null && !middleName.trim().isEmpty()) {
            return String.format("%s %s %s", firstName, middleName.trim(), lastName).trim();
        }
        return String.format("%s %s", firstName, lastName).trim();
    }

    /**
     * Backward-compatible address getter.
     */
    public String getAddress() {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isBlank()) sb.append(addressLine1);
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(state);
        }
        if (country != null && !country.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(country);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Checks if student is currently actively enrolled.
     */
    public boolean isEnrolled() {
        return active && status == StudentStatus.ACTIVE;
    }
}
