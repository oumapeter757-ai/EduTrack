package com.peterscode.edutrack.student.mapper;

import com.peterscode.edutrack.student.dto.CreateStudentRequestDto;
import com.peterscode.edutrack.student.dto.StudentResponseDto;
import com.peterscode.edutrack.student.dto.StudentSummaryDto;
import com.peterscode.edutrack.student.dto.UpdateStudentRequestDto;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import org.springframework.stereotype.Component;
import com.peterscode.edutrack.shared.PhoneNumberNormalizer;

@Component
public class StudentMapper {

    public StudentResponseDto toDto(Student student) {
        return toDto(student, com.peterscode.edutrack.user.entity.Role.STUDENT);
    }

    public Student toEntity(CreateStudentRequestDto dto, Long schoolId) {
        if (dto == null) {
            return null;
        }

        return Student.builder()
                .schoolId(schoolId)
                .classLevelId(dto.getClassLevelId())
                .streamId(dto.getStreamId())
                .academicYearId(dto.getAcademicYearId())
                .userId(dto.getUserId())
                .admissionNumber(dto.getAdmissionNumber() != null ? dto.getAdmissionNumber().trim().toUpperCase() : null)
                .firstName(blankToNull(dto.getFirstName()))
                .lastName(blankToNull(dto.getLastName()))
                .middleName(blankToNull(dto.getMiddleName()))
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .bloodGroup(dto.getBloodGroup())
                .email(email(dto.getEmail()))
                .phone(phone(dto.getPhone()))
                .addressLine1(blankToNull(dto.getAddressLine1()))
                .addressLine2(blankToNull(dto.getAddressLine2()))
                .city(blankToNull(dto.getCity()))
                .state(blankToNull(dto.getState()))
                .country(blankToNull(dto.getCountry()))
                .postalCode(blankToNull(dto.getPostalCode()))
                .profileImageUrl(blankToNull(dto.getProfileImageUrl()))
                .admissionDate(dto.getAdmissionDate())
                .gradeLevel(blankToNull(dto.getGradeLevel()))
                .section(upper(dto.getSection()))
                .rollNumber(blankToNull(dto.getRollNumber()))
                .academicYear(blankToNull(dto.getAcademicYear()))
                .status(dto.getStatus() != null ? dto.getStatus() : StudentStatus.ACTIVE)
                .emergencyContactName(blankToNull(dto.getEmergencyContactName()))
                .emergencyContactPhone(phone(dto.getEmergencyContactPhone()))
                .emergencyContactRelationship(blankToNull(dto.getEmergencyContactRelationship()))
                .guardianName(blankToNull(dto.getGuardianName()))
                .guardianEmail(email(dto.getGuardianEmail()))
                .guardianPhone(phone(dto.getGuardianPhone()))
                .guardianRelationship(blankToNull(dto.getGuardianRelationship()))
                .medicalNotes(blankToNull(dto.getMedicalNotes()))
                .active((dto.getStatus() == null ? StudentStatus.ACTIVE : dto.getStatus()) == StudentStatus.ACTIVE)
                .build();
    }

    public StudentResponseDto toDto(Student student, com.peterscode.edutrack.user.entity.Role viewerRole) {
        if (student == null) {
            return null;
        }

        return StudentResponseDto.builder()
                .id(student.getId())
                .schoolId(student.getSchoolId())
                .classLevelId(student.getClassLevelId())
                .streamId(student.getStreamId())
                .academicYearId(student.getAcademicYearId())
                .userId(student.getUserId())
                .admissionNumber(student.getAdmissionNumber())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .middleName(student.getMiddleName())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .gender(student.getGender())
                .bloodGroup(student.getBloodGroup())
                .email(student.getEmail())
                .phone(student.getPhone())
                .addressLine1(student.getAddressLine1())
                .addressLine2(student.getAddressLine2())
                .city(student.getCity())
                .state(student.getState())
                .country(student.getCountry())
                .postalCode(student.getPostalCode())
                .address(student.getAddress())
                .profileImageUrl(student.getProfileImageUrl())
                .admissionDate(student.getAdmissionDate())
                .gradeLevel(student.getGradeLevel())
                .section(student.getSection())
                .rollNumber(student.getRollNumber())
                .academicYear(student.getAcademicYear())
                .status(student.getStatus())
                .emergencyContactName(student.getEmergencyContactName())
                .emergencyContactPhone(student.getEmergencyContactPhone())
                .emergencyContactRelationship(student.getEmergencyContactRelationship())
                .guardianName(student.getGuardianName())
                .guardianEmail(student.getGuardianEmail())
                .guardianPhone(student.getGuardianPhone())
                .guardianRelationship(student.getGuardianRelationship())
                .medicalNotes(viewerRole == com.peterscode.edutrack.user.entity.Role.SUPER_ADMIN
                        || viewerRole == com.peterscode.edutrack.user.entity.Role.SCHOOL_ADMIN
                        ? student.getMedicalNotes() : null)
                .active(student.isActive())
                .enrolled(student.isEnrolled())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    public StudentSummaryDto toSummaryDto(Student student) {
        if (student == null) {
            return null;
        }

        return StudentSummaryDto.builder()
                .id(student.getId())
                .schoolId(student.getSchoolId())
                .userId(student.getUserId())
                .admissionNumber(student.getAdmissionNumber())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .fullName(student.getFullName())
                .email(student.getEmail())
                .phone(student.getPhone())
                .gradeLevel(student.getGradeLevel())
                .section(student.getSection())
                .rollNumber(student.getRollNumber())
                .gender(student.getGender())
                .status(student.getStatus())
                .profileImageUrl(student.getProfileImageUrl())
                .active(student.isActive())
                .build();
    }

    public void updateEntityFromDto(UpdateStudentRequestDto dto, Student student) {
        if (dto == null || student == null) {
            return;
        }
        if (dto.getClassLevelId() != null) student.setClassLevelId(dto.getClassLevelId());
        if (dto.getStreamId() != null) student.setStreamId(dto.getStreamId());
        if (dto.getAcademicYearId() != null) student.setAcademicYearId(dto.getAcademicYearId());

        if (dto.getFirstName() != null) {
            student.setFirstName(blankToNull(dto.getFirstName()));
        }
        if (dto.getLastName() != null) {
            student.setLastName(blankToNull(dto.getLastName()));
        }
        if (dto.getMiddleName() != null) {
            student.setMiddleName(blankToNull(dto.getMiddleName()));
        }
        if (dto.getDateOfBirth() != null) {
            student.setDateOfBirth(dto.getDateOfBirth());
        }
        if (dto.getGender() != null) {
            student.setGender(dto.getGender());
        }
        if (dto.getBloodGroup() != null) {
            student.setBloodGroup(dto.getBloodGroup());
        }
        if (dto.getEmail() != null) {
            student.setEmail(email(dto.getEmail()));
        }
        if (dto.getPhone() != null) {
            student.setPhone(phone(dto.getPhone()));
        }
        if (dto.getAddressLine1() != null) {
            student.setAddressLine1(blankToNull(dto.getAddressLine1()));
        }
        if (dto.getAddressLine2() != null) {
            student.setAddressLine2(blankToNull(dto.getAddressLine2()));
        }
        if (dto.getCity() != null) {
            student.setCity(blankToNull(dto.getCity()));
        }
        if (dto.getState() != null) {
            student.setState(blankToNull(dto.getState()));
        }
        if (dto.getCountry() != null) {
            student.setCountry(blankToNull(dto.getCountry()));
        }
        if (dto.getPostalCode() != null) {
            student.setPostalCode(blankToNull(dto.getPostalCode()));
        }
        if (dto.getProfileImageUrl() != null) {
            student.setProfileImageUrl(blankToNull(dto.getProfileImageUrl()));
        }
        if (dto.getAdmissionDate() != null) {
            student.setAdmissionDate(dto.getAdmissionDate());
        }
        if (dto.getGradeLevel() != null) {
            student.setGradeLevel(blankToNull(dto.getGradeLevel()));
        }
        if (dto.getSection() != null) {
            student.setSection(upper(dto.getSection()));
        }
        if (dto.getRollNumber() != null) {
            student.setRollNumber(blankToNull(dto.getRollNumber()));
        }
        if (dto.getAcademicYear() != null) {
            student.setAcademicYear(blankToNull(dto.getAcademicYear()));
        }
        if (dto.getEmergencyContactName() != null) {
            student.setEmergencyContactName(blankToNull(dto.getEmergencyContactName()));
        }
        if (dto.getEmergencyContactPhone() != null) {
            student.setEmergencyContactPhone(phone(dto.getEmergencyContactPhone()));
        }
        if (dto.getEmergencyContactRelationship() != null) {
            student.setEmergencyContactRelationship(blankToNull(dto.getEmergencyContactRelationship()));
        }
        if (dto.getGuardianName() != null) {
            student.setGuardianName(blankToNull(dto.getGuardianName()));
        }
        if (dto.getGuardianEmail() != null) {
            student.setGuardianEmail(email(dto.getGuardianEmail()));
        }
        if (dto.getGuardianPhone() != null) {
            student.setGuardianPhone(phone(dto.getGuardianPhone()));
        }
        if (dto.getGuardianRelationship() != null) {
            student.setGuardianRelationship(blankToNull(dto.getGuardianRelationship()));
        }
        if (dto.getMedicalNotes() != null) {
            student.setMedicalNotes(blankToNull(dto.getMedicalNotes()));
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String email(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static String upper(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static String phone(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        String phone = PhoneNumberNormalizer.normalize(normalized);
        if (phone.length() > 50) {
            throw new IllegalArgumentException("Phone number cannot exceed 50 characters");
        }
        return phone;
    }
}
