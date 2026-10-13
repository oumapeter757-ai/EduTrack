package com.peterscode.edutrack.student.mapper;

import com.peterscode.edutrack.student.dto.CreateStudentRequestDto;
import com.peterscode.edutrack.student.dto.StudentResponseDto;
import com.peterscode.edutrack.student.dto.StudentSummaryDto;
import com.peterscode.edutrack.student.dto.UpdateStudentRequestDto;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentMapperTest {

    private StudentMapper studentMapper;

    @BeforeEach
    void setUp() {
        studentMapper = new StudentMapper();
    }

    @Test
    @DisplayName("toEntity maps create DTO to entity accurately")
    void toEntity_MapsAllFields() {
        CreateStudentRequestDto dto = CreateStudentRequestDto.builder()
                .admissionNumber("adm-001")
                .firstName("John")
                .middleName("Robert")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(2010, 5, 15))
                .gender(Gender.MALE)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .email("JOHN.DOE@school.com")
                .phone("+1234567890")
                .addressLine1("123 Maple St")
                .city("Springfield")
                .state("IL")
                .country("USA")
                .postalCode("62701")
                .gradeLevel("Grade 10")
                .section("a")
                .rollNumber("101")
                .academicYear("2025-2026")
                .status(StudentStatus.ACTIVE)
                .emergencyContactName("Jane Doe")
                .emergencyContactPhone("+1987654321")
                .emergencyContactRelationship("Mother")
                .guardianName("Richard Doe")
                .guardianEmail("richard@example.com")
                .guardianPhone("+1555123456")
                .guardianRelationship("Father")
                .medicalNotes("No known allergies")
                .userId(12L)
                .build();

        Student entity = studentMapper.toEntity(dto, 1L);

        assertThat(entity).isNotNull();
        assertThat(entity.getSchoolId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(12L);
        assertThat(entity.getAdmissionNumber()).isEqualTo("ADM-001");
        assertThat(entity.getFirstName()).isEqualTo("John");
        assertThat(entity.getMiddleName()).isEqualTo("Robert");
        assertThat(entity.getLastName()).isEqualTo("Doe");
        assertThat(entity.getFullName()).isEqualTo("John Robert Doe");
        assertThat(entity.getEmail()).isEqualTo("john.doe@school.com");
        assertThat(entity.getSection()).isEqualTo("A");
        assertThat(entity.getGradeLevel()).isEqualTo("Grade 10");
        assertThat(entity.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(entity.isActive()).isTrue();
        assertThat(entity.isEnrolled()).isTrue();
    }

    @Test
    @DisplayName("toDto maps entity to full response DTO accurately")
    void toDto_MapsAllFields() {
        Student entity = Student.builder()
                .schoolId(2L)
                .userId(20L)
                .admissionNumber("ADM-002")
                .firstName("Alice")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(2011, 8, 20))
                .gender(Gender.FEMALE)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .email("alice@school.com")
                .gradeLevel("Grade 9")
                .section("B")
                .status(StudentStatus.ACTIVE)
                .active(true)
                .build();
        entity.setId(10L);

        StudentResponseDto dto = studentMapper.toDto(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getSchoolId()).isEqualTo(2L);
        assertThat(dto.getUserId()).isEqualTo(20L);
        assertThat(dto.getAdmissionNumber()).isEqualTo("ADM-002");
        assertThat(dto.getFullName()).isEqualTo("Alice Smith");
        assertThat(dto.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(dto.getBloodGroup()).isEqualTo(BloodGroup.A_POSITIVE);
        assertThat(dto.isEnrolled()).isTrue();
    }

    @Test
    @DisplayName("toSummaryDto maps entity to lightweight summary DTO")
    void toSummaryDto_MapsSummaryFields() {
        Student entity = Student.builder()
                .schoolId(3L)
                .admissionNumber("ADM-003")
                .firstName("Bob")
                .lastName("Brown")
                .gradeLevel("Grade 11")
                .section("C")
                .rollNumber("15")
                .gender(Gender.MALE)
                .status(StudentStatus.ACTIVE)
                .active(true)
                .build();
        entity.setId(15L);

        StudentSummaryDto summary = studentMapper.toSummaryDto(entity);

        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo(15L);
        assertThat(summary.getFullName()).isEqualTo("Bob Brown");
        assertThat(summary.getGradeLevel()).isEqualTo("Grade 11");
        assertThat(summary.getRollNumber()).isEqualTo("15");
    }

    @Test
    @DisplayName("updateEntityFromDto updates editable student fields")
    void updateEntityFromDto_Success() {
        Student student = Student.builder()
                .schoolId(1L)
                .admissionNumber("ADM-001")
                .firstName("OldFirst")
                .lastName("OldLast")
                .email("old@school.com")
                .gradeLevel("Grade 8")
                .build();

        UpdateStudentRequestDto updateDto = UpdateStudentRequestDto.builder()
                .firstName("NewFirst")
                .lastName("NewLast")
                .email("NEW@SCHOOL.COM")
                .gradeLevel("Grade 9")
                .city("Metropolis")
                .medicalNotes("Asthma")
                .build();

        studentMapper.updateEntityFromDto(updateDto, student);

        assertThat(student.getFirstName()).isEqualTo("NewFirst");
        assertThat(student.getLastName()).isEqualTo("NewLast");
        assertThat(student.getEmail()).isEqualTo("new@school.com");
        assertThat(student.getGradeLevel()).isEqualTo("Grade 9");
        assertThat(student.getCity()).isEqualTo("Metropolis");
        assertThat(student.getMedicalNotes()).isEqualTo("Asthma");
        assertThat(student.getAdmissionNumber()).isEqualTo("ADM-001"); // Unchanged
    }
}
