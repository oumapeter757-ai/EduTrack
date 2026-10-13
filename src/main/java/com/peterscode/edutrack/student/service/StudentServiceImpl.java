package com.peterscode.edutrack.student.service;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.exception.UserNotFound;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.dto.*;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.exception.StudentCapacityExceededException;
import com.peterscode.edutrack.student.exception.StudentModuleDisabledException;
import com.peterscode.edutrack.student.exception.StudentNotFoundException;
import com.peterscode.edutrack.student.mapper.StudentMapper;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.student.repository.StudentSpecification;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final StudentMapper studentMapper;

    @Transactional
    @Override
    public StudentResponseDto createStudent(CreateStudentRequestDto requestDto, UserPrincipal principal) {
        Long schoolId = resolveSchoolIdForCreation(requestDto.getSchoolId(), principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));

        if (!school.isActive()) {
            throw new IllegalStateException(String.format("Cannot create student for inactive school ID: %d", schoolId));
        }

        if (!school.isModuleEnabled(ServiceModule.STUDENT_MANAGEMENT)) {
            throw new StudentModuleDisabledException(schoolId);
        }

        if (school.getMaxStudents() != null && school.getMaxStudents() > 0) {
            long currentActive = studentRepository.countBySchoolIdAndActiveTrue(schoolId);
            if (currentActive >= school.getMaxStudents()) {
                throw new StudentCapacityExceededException(schoolId, school.getMaxStudents());
            }
        }

        String admissionNumber = requestDto.getAdmissionNumber().trim().toUpperCase();
        if (studentRepository.existsBySchoolIdAndAdmissionNumber(schoolId, admissionNumber)) {
            throw new DuplicateResourceException("Student", "admissionNumber", admissionNumber);
        }

        if (requestDto.getEmail() != null && !requestDto.getEmail().isBlank()) {
            String email = requestDto.getEmail().trim().toLowerCase();
            if (studentRepository.existsBySchoolIdAndEmail(schoolId, email)) {
                throw new DuplicateResourceException("Student", "email", email);
            }
        }

        if (requestDto.getUserId() != null) {
            validateAndLinkUser(requestDto.getUserId(), schoolId, null);
        }

        Student student = studentMapper.toEntity(requestDto, schoolId);
        Student savedStudent = studentRepository.save(student);
        log.info("Created new student with ID: {} and admission number: {} for school ID: {}",
                savedStudent.getId(), savedStudent.getAdmissionNumber(), schoolId);

        return studentMapper.toDto(savedStudent);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getStudentById(Long id, UserPrincipal principal) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateStudentAccess(student, principal);

        return studentMapper.toDto(student);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getStudentByAdmissionNumber(String admissionNumber, UserPrincipal principal) {
        Long schoolId = resolveSchoolIdForQuery(null, principal);
        String formattedAdmissionNumber = admissionNumber.trim().toUpperCase();

        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, formattedAdmissionNumber)
                .orElseThrow(() -> StudentNotFoundException.byAdmissionNumber(formattedAdmissionNumber));

        validateStudentAccess(student, principal);

        return studentMapper.toDto(student);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getCurrentStudentProfile(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedOperationException("User is not authenticated");
        }

        Student student = studentRepository.findByUserId(principal.getId())
                .orElseThrow(() -> StudentNotFoundException.byUserId(principal.getId()));

        if (principal.getSchoolId() != null && !principal.getSchoolId().equals(student.getSchoolId())) {
            throw new UnauthorizedOperationException("Student belongs to a different school");
        }

        return studentMapper.toDto(student);
    }

    @Transactional
    @Override
    public StudentResponseDto updateStudent(Long id, UpdateStudentRequestDto requestDto, UserPrincipal principal) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);

        if (requestDto.getEmail() != null && !requestDto.getEmail().isBlank()) {
            String email = requestDto.getEmail().trim().toLowerCase();
            if (studentRepository.existsBySchoolIdAndEmailAndIdNot(student.getSchoolId(), email, id)) {
                throw new DuplicateResourceException("Student", "email", email);
            }
        }

        studentMapper.updateEntityFromDto(requestDto, student);
        Student updatedStudent = studentRepository.save(student);
        log.info("Updated student ID: {} for school ID: {}", id, student.getSchoolId());

        return studentMapper.toDto(updatedStudent);
    }

    @Transactional
    @Override
    public StudentResponseDto updateStudentStatus(Long id, UpdateStudentStatusRequestDto requestDto, UserPrincipal principal) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);

        StudentStatus oldStatus = student.getStatus();
        boolean oldActive = student.isActive();

        student.setStatus(requestDto.getStatus());
        if (requestDto.getActive() != null) {
            student.setActive(requestDto.getActive());
        } else if (requestDto.getStatus() == StudentStatus.GRADUATED
                || requestDto.getStatus() == StudentStatus.EXPELLED
                || requestDto.getStatus() == StudentStatus.TRANSFERRED
                || requestDto.getStatus() == StudentStatus.WITHDRAWN
                || requestDto.getStatus() == StudentStatus.INACTIVE) {
            student.setActive(false);
        } else if (requestDto.getStatus() == StudentStatus.ACTIVE) {
            student.setActive(true);
        }

        if (!oldActive && student.isActive()) {
            School school = schoolRepository.findById(student.getSchoolId())
                    .orElseThrow(() -> new SchoolNotFoundException(student.getSchoolId()));
            if (school.getMaxStudents() != null && school.getMaxStudents() > 0) {
                long currentActive = studentRepository.countBySchoolIdAndActiveTrue(student.getSchoolId());
                if (currentActive > school.getMaxStudents()) {
                    throw new StudentCapacityExceededException(student.getSchoolId(), school.getMaxStudents());
                }
            }
        }

        Student savedStudent = studentRepository.save(student);
        log.info("Updated status for student ID: {} from {} (active: {}) to {} (active: {})",
                id, oldStatus, oldActive, savedStudent.getStatus(), savedStudent.isActive());

        return studentMapper.toDto(savedStudent);
    }

    @Transactional
    @Override
    public void bulkUpdateStatus(BulkStudentStatusUpdateRequestDto requestDto, UserPrincipal principal) {
        if (requestDto.getStudentIds() == null || requestDto.getStudentIds().isEmpty()) {
            return;
        }

        Long schoolId = resolveSchoolIdForQuery(null, principal);
        List<Student> students = studentRepository.findAllBySchoolIdAndIdIn(schoolId, requestDto.getStudentIds());

        for (Student student : students) {
            student.setStatus(requestDto.getStatus());
            if (requestDto.getActive() != null) {
                student.setActive(requestDto.getActive());
            } else if (requestDto.getStatus() == StudentStatus.ACTIVE) {
                student.setActive(true);
            } else {
                student.setActive(false);
            }
        }

        studentRepository.saveAll(students);
        log.info("Bulk updated {} students to status: {} in school ID: {}",
                students.size(), requestDto.getStatus(), schoolId);
    }

    @Transactional
    @Override
    public void deleteStudent(Long id, UserPrincipal principal) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);

        student.setActive(false);
        student.setStatus(StudentStatus.WITHDRAWN);
        studentRepository.save(student);
        log.info("Soft deleted student ID: {} in school ID: {}", id, student.getSchoolId());
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<StudentSummaryDto> searchStudents(StudentSearchCriteria criteria, Pageable pageable, UserPrincipal principal) {
        Long enforcedSchoolId = null;
        if (principal.getRole() != Role.SUPER_ADMIN) {
            enforcedSchoolId = principal.getSchoolId();
        } else if (criteria != null && criteria.getSchoolId() != null) {
            enforcedSchoolId = criteria.getSchoolId();
        }

        Specification<Student> spec = StudentSpecification.withCriteria(criteria, enforcedSchoolId);
        Page<StudentSummaryDto> page = studentRepository.findAll(spec, pageable)
                .map(studentMapper::toSummaryDto);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentStatsDto getStudentStatistics(Long schoolId, UserPrincipal principal) {
        Long targetSchoolId = resolveSchoolIdForQuery(schoolId, principal);

        School school = schoolRepository.findById(targetSchoolId)
                .orElseThrow(() -> new SchoolNotFoundException(targetSchoolId));

        long totalStudents = studentRepository.countBySchoolId(targetSchoolId);
        long activeStudents = studentRepository.countBySchoolIdAndActiveTrue(targetSchoolId);
        long inactiveStudents = studentRepository.countBySchoolIdAndActiveFalse(targetSchoolId);

        Map<StudentStatus, Long> statusCounts = new EnumMap<>(StudentStatus.class);
        for (StudentStatus status : StudentStatus.values()) {
            statusCounts.put(status, studentRepository.countBySchoolIdAndStatus(targetSchoolId, status));
        }

        Map<Gender, Long> genderCounts = new EnumMap<>(Gender.class);
        for (Gender gender : Gender.values()) {
            genderCounts.put(gender, studentRepository.countBySchoolIdAndGender(targetSchoolId, gender));
        }

        // Calculate grade level counts
        List<Student> allSchoolStudents = studentRepository.findBySchoolId(targetSchoolId, Pageable.unpaged()).getContent();
        Map<String, Long> gradeLevelCounts = allSchoolStudents.stream()
                .filter(s -> s.getGradeLevel() != null && !s.getGradeLevel().isBlank())
                .collect(Collectors.groupingBy(Student::getGradeLevel, Collectors.counting()));

        Integer maxCapacity = school.getMaxStudents();
        Long capacityRemaining = maxCapacity != null ? Math.max(0, maxCapacity - activeStudents) : null;
        Double utilization = (maxCapacity != null && maxCapacity > 0)
                ? (double) activeStudents / maxCapacity * 100.0
                : null;

        return StudentStatsDto.builder()
                .schoolId(targetSchoolId)
                .totalStudents(totalStudents)
                .activeStudents(activeStudents)
                .inactiveStudents(inactiveStudents)
                .maxCapacity(maxCapacity)
                .capacityRemaining(capacityRemaining)
                .capacityUtilizationPercentage(utilization)
                .statusCounts(statusCounts)
                .gradeLevelCounts(gradeLevelCounts)
                .genderCounts(genderCounts)
                .build();
    }

    @Transactional
    @Override
    public StudentResponseDto linkUserAccount(Long studentId, Long userId, UserPrincipal principal) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateAdminAccess(student.getSchoolId(), principal);
        validateAndLinkUser(userId, student.getSchoolId(), studentId);

        student.setUserId(userId);
        Student savedStudent = studentRepository.save(student);
        log.info("Linked user ID: {} to student ID: {}", userId, studentId);

        return studentMapper.toDto(savedStudent);
    }

    @Transactional
    @Override
    public StudentResponseDto unlinkUserAccount(Long studentId, UserPrincipal principal) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateAdminAccess(student.getSchoolId(), principal);

        student.setUserId(null);
        Student savedStudent = studentRepository.save(student);
        log.info("Unlinked user from student ID: {}", studentId);

        return studentMapper.toDto(savedStudent);
    }

    private Long resolveSchoolIdForCreation(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId == null) {
                throw new IllegalArgumentException("School ID is required for Super Admin to create a student");
            }
            return explicitSchoolId;
        }

        Long adminSchoolId = principal.getSchoolId();
        if (adminSchoolId == null) {
            throw new UnauthorizedOperationException("Current user has no associated school");
        }

        if (explicitSchoolId != null && !explicitSchoolId.equals(adminSchoolId)) {
            throw new UnauthorizedOperationException("Cannot create a student for a different school tenant");
        }

        return adminSchoolId;
    }

    private Long resolveSchoolIdForQuery(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId != null) {
                return explicitSchoolId;
            }
            throw new IllegalArgumentException("School ID must be specified for this operation");
        }
        return principal.getSchoolId();
    }

    private void validateAdminAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() != Role.SCHOOL_ADMIN || !schoolId.equals(principal.getSchoolId())) {
            throw new UnauthorizedOperationException("You do not have administrative permission for this school");
        }
    }

    private void validateStudentAccess(Student student, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }

        if (!student.getSchoolId().equals(principal.getSchoolId())) {
            throw new UnauthorizedOperationException("Cannot access student from another school tenant");
        }

        if (principal.getRole() == Role.STUDENT) {
            if (student.getUserId() == null || !student.getUserId().equals(principal.getId())) {
                throw new UnauthorizedOperationException("Students are only permitted to view their own profile");
            }
        }
    }

    private void validateAndLinkUser(Long userId, Long schoolId, Long currentStudentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found with id: " + userId));

        if (!schoolId.equals(user.getSchoolId())) {
            throw new UnauthorizedOperationException("Linked user does not belong to the same school");
        }

        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Linked user must have role STUDENT");
        }

        studentRepository.findByUserId(userId).ifPresent(existing -> {
            if (currentStudentId == null || !existing.getId().equals(currentStudentId)) {
                throw new DuplicateResourceException(
                        String.format("User ID %d is already linked to student admission number: %s",
                                userId, existing.getAdmissionNumber())
                );
            }
        });
    }
}
