package com.peterscode.edutrack.student.mapper;

import com.peterscode.edutrack.student.dto.CreateStudentRequestDto;
import com.peterscode.edutrack.student.dto.StudentResponseDto;
import com.peterscode.edutrack.student.dto.StudentSummaryDto;
import com.peterscode.edutrack.student.dto.UpdateStudentRequestDto;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import org.springframework.stereotype.Component;

@Component
public class StudentMapper {

    public Student toEntity(CreateStudentRequestDto dto, Long schoolId) {
        if (dto == null) {
            return null;
        }

        return Student.builder()
                .schoolId(schoolId)
                .userId(dto.getUserId())
                .admissionNumber(dto.getAdmissionNumber() != null ? dto.getAdmissionNumber().trim().toUpperCase() : null)
                .firstName(dto.getFirstName() != null ? dto.getFirstName().trim() : null)
                .lastName(dto.getLastName() != null ? dto.getLastName().trim() : null)
                .middleName(dto.getMiddleName() != null ? dto.getMiddleName().trim() : null)
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .bloodGroup(dto.getBloodGroup())
                .email(dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : null)
                .phone(dto.getPhone() != null ? dto.getPhone().trim() : null)
                .addressLine1(dto.getAddressLine1() != null ? dto.getAddressLine1().trim() : null)
                .addressLine2(dto.getAddressLine2() != null ? dto.getAddressLine2().trim() : null)
                .city(dto.getCity() != null ? dto.getCity().trim() : null)
                .state(dto.getState() != null ? dto.getState().trim() : null)
                .country(dto.getCountry() != null ? dto.getCountry().trim() : null)
                .postalCode(dto.getPostalCode() != null ? dto.getPostalCode().trim() : null)
                .profileImageUrl(dto.getProfileImageUrl() != null ? dto.getProfileImageUrl().trim() : null)
                .admissionDate(dto.getAdmissionDate())
                .gradeLevel(dto.getGradeLevel() != null ? dto.getGradeLevel().trim() : null)
                .section(dto.getSection() != null ? dto.getSection().trim().toUpperCase() : null)
                .rollNumber(dto.getRollNumber() != null ? dto.getRollNumber().trim() : null)
                .academicYear(dto.getAcademicYear() != null ? dto.getAcademicYear().trim() : null)
                .status(dto.getStatus() != null ? dto.getStatus() : StudentStatus.ACTIVE)
                .emergencyContactName(dto.getEmergencyContactName() != null ? dto.getEmergencyContactName().trim() : null)
                .emergencyContactPhone(dto.getEmergencyContactPhone() != null ? dto.getEmergencyContactPhone().trim() : null)
                .emergencyContactRelationship(dto.getEmergencyContactRelationship() != null ? dto.getEmergencyContactRelationship().trim() : null)
                .guardianName(dto.getGuardianName() != null ? dto.getGuardianName().trim() : null)
                .guardianEmail(dto.getGuardianEmail() != null ? dto.getGuardianEmail().trim().toLowerCase() : null)
                .guardianPhone(dto.getGuardianPhone() != null ? dto.getGuardianPhone().trim() : null)
                .guardianRelationship(dto.getGuardianRelationship() != null ? dto.getGuardianRelationship().trim() : null)
                .medicalNotes(dto.getMedicalNotes() != null ? dto.getMedicalNotes().trim() : null)
                .active(true)
                .build();
    }

    public StudentResponseDto toDto(Student student) {
        if (student == null) {
            return null;
        }

        return StudentResponseDto.builder()
                .id(student.getId())
                .schoolId(student.getSchoolId())
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
                .medicalNotes(student.getMedicalNotes())
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

        if (dto.getFirstName() != null) {
            student.setFirstName(dto.getFirstName().trim());
        }
        if (dto.getLastName() != null) {
            student.setLastName(dto.getLastName().trim());
        }
        if (dto.getMiddleName() != null) {
            student.setMiddleName(dto.getMiddleName().trim());
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
            student.setEmail(dto.getEmail().trim().toLowerCase());
        }
        if (dto.getPhone() != null) {
            student.setPhone(dto.getPhone().trim());
        }
        if (dto.getAddressLine1() != null) {
            student.setAddressLine1(dto.getAddressLine1().trim());
        }
        if (dto.getAddressLine2() != null) {
            student.setAddressLine2(dto.getAddressLine2().trim());
        }
        if (dto.getCity() != null) {
            student.setCity(dto.getCity().trim());
        }
        if (dto.getState() != null) {
            student.setState(dto.getState().trim());
        }
        if (dto.getCountry() != null) {
            student.setCountry(dto.getCountry().trim());
        }
        if (dto.getPostalCode() != null) {
            student.setPostalCode(dto.getPostalCode().trim());
        }
        if (dto.getProfileImageUrl() != null) {
            student.setProfileImageUrl(dto.getProfileImageUrl().trim());
        }
        if (dto.getAdmissionDate() != null) {
            student.setAdmissionDate(dto.getAdmissionDate());
        }
        if (dto.getGradeLevel() != null) {
            student.setGradeLevel(dto.getGradeLevel().trim());
        }
        if (dto.getSection() != null) {
            student.setSection(dto.getSection().trim().toUpperCase());
        }
        if (dto.getRollNumber() != null) {
            student.setRollNumber(dto.getRollNumber().trim());
        }
        if (dto.getAcademicYear() != null) {
            student.setAcademicYear(dto.getAcademicYear().trim());
        }
        if (dto.getEmergencyContactName() != null) {
            student.setEmergencyContactName(dto.getEmergencyContactName().trim());
        }
        if (dto.getEmergencyContactPhone() != null) {
            student.setEmergencyContactPhone(dto.getEmergencyContactPhone().trim());
        }
        if (dto.getEmergencyContactRelationship() != null) {
            student.setEmergencyContactRelationship(dto.getEmergencyContactRelationship().trim());
        }
        if (dto.getGuardianName() != null) {
            student.setGuardianName(dto.getGuardianName().trim());
        }
        if (dto.getGuardianEmail() != null) {
            student.setGuardianEmail(dto.getGuardianEmail().trim().toLowerCase());
        }
        if (dto.getGuardianPhone() != null) {
            student.setGuardianPhone(dto.getGuardianPhone().trim());
        }
        if (dto.getGuardianRelationship() != null) {
            student.setGuardianRelationship(dto.getGuardianRelationship().trim());
        }
        if (dto.getMedicalNotes() != null) {
            student.setMedicalNotes(dto.getMedicalNotes().trim());
        }
    }
}
