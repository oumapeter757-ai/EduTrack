package com.peterscode.edutrack.student.service;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.PlanGuard;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.exception.UserNotFound;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.dto.*;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.exception.StudentNotFoundException;
import com.peterscode.edutrack.student.exception.StudentRequestException;
import com.peterscode.edutrack.student.mapper.StudentMapper;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.student.repository.StudentSpecification;
import com.peterscode.edutrack.student.audit.StudentAuditService;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.peterscode.edutrack.shared.PageableSortValidator;
import com.peterscode.edutrack.shared.PhoneNumberNormalizer;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final int MAX_BULK_SIZE = 200;

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final StudentMapper studentMapper;
    private final PlanGuard planGuard;
    private final ClassLevelRepository classLevelRepository;
    private final StreamRepository streamRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentAuditService auditService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public StudentResponseDto createStudent(CreateStudentRequestDto requestDto, UserPrincipal principal) {
        requirePrincipal(principal);
        requireAdmin(principal);
        Long schoolId = resolveSchoolIdForCreation(requestDto.getSchoolId(), principal);

        StudentStatus requestedStatus = requestDto.getStatus() == null ? StudentStatus.ACTIVE : requestDto.getStatus();
        if (requestedStatus == StudentStatus.ACTIVE) {
            planGuard.assertStudentCapacity(schoolId, 1);
        }
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));

        if (!school.isActive()) {
            throw new StudentRequestException("Student operation is not available");
        }
        validateReferences(schoolId, requestDto.getClassLevelId(), requestDto.getStreamId(), requestDto.getAcademicYearId());

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
        Student savedStudent = studentRepository.saveAndFlush(student);
        log.info("Created new student with ID: {} and admission number: {} for school ID: {}",
                savedStudent.getId(), savedStudent.getAdmissionNumber(), schoolId);

        return studentMapper.toDto(savedStudent, principal.getRole());
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getStudentById(Long id, UserPrincipal principal) {
        requirePrincipal(principal);
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(id)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), id))
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateStudentAccess(student, principal);

        return studentMapper.toDto(student, principal.getRole());
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getStudentByAdmissionNumber(String admissionNumber, UserPrincipal principal) {
        return getStudentByAdmissionNumber(admissionNumber, null, principal);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getStudentByAdmissionNumber(String admissionNumber, Long requestedSchoolId,
                                                          UserPrincipal principal) {
        requirePrincipal(principal);
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN
                && principal.getRole() != Role.TEACHER) {
            throw new UnauthorizedOperationException("Access denied");
        }
        Long schoolId = resolveSchoolIdForQuery(requestedSchoolId, principal);
        String formattedAdmissionNumber = admissionNumber.trim().toUpperCase();

        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, formattedAdmissionNumber)
                .orElseThrow(() -> StudentNotFoundException.byAdmissionNumber(formattedAdmissionNumber));

        validateStudentAccess(student, principal);

        return studentMapper.toDto(student, principal.getRole());
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseDto getCurrentStudentProfile(UserPrincipal principal) {
        if (principal == null || principal.getId() == null || principal.getRole() != Role.STUDENT) {
            throw new UnauthorizedOperationException("User is not authenticated");
        }

        Long schoolId = requireSchool(principal);
        Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> StudentNotFoundException.byUserId(principal.getId()));

        if (principal.getSchoolId() != null && !principal.getSchoolId().equals(student.getSchoolId())) {
            throw new UnauthorizedOperationException("Student belongs to a different school");
        }

        return studentMapper.toDto(student, principal.getRole());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public StudentResponseDto updateStudent(Long id, UpdateStudentRequestDto requestDto, UserPrincipal principal) {
        requirePrincipal(principal);
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(id)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), id))
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);
        validateReferences(student.getSchoolId(), requestDto.getClassLevelId(), requestDto.getStreamId(), requestDto.getAcademicYearId());

        if (requestDto.getEmail() != null && !requestDto.getEmail().isBlank()) {
            String email = requestDto.getEmail().trim().toLowerCase();
            if (studentRepository.existsBySchoolIdAndEmailAndIdNot(student.getSchoolId(), email, id)) {
                throw new DuplicateResourceException("Student", "email", email);
            }
        }

        studentMapper.updateEntityFromDto(requestDto, student);
        Student updatedStudent = studentRepository.saveAndFlush(student);
        log.info("Updated student ID: {} for school ID: {}", id, student.getSchoolId());

        return studentMapper.toDto(updatedStudent, principal.getRole());
    }

    @Transactional
    @Override
    public StudentResponseDto updateStudentStatus(Long id, UpdateStudentStatusRequestDto requestDto, UserPrincipal principal) {
        requirePrincipal(principal);
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(id)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), id))
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);

        StudentStatus oldStatus = student.getStatus();
        boolean oldActive = student.isActive();

        if (oldStatus != StudentStatus.ACTIVE && requestDto.getStatus() == StudentStatus.ACTIVE)
            planGuard.assertStudentCapacity(student.getSchoolId(), 1);
        student.setStatus(requestDto.getStatus());

        Student savedStudent = studentRepository.saveAndFlush(student);
        auditService.record(id, student.getSchoolId(), "STATUS_CHANGE", principal,
                oldStatus == null ? null : oldStatus.name(), savedStudent.getStatus().name());
        log.info("Updated status for student ID: {} from {} (active: {}) to {} (active: {})",
                id, oldStatus, oldActive, savedStudent.getStatus(), savedStudent.isActive());

        return studentMapper.toDto(savedStudent, principal.getRole());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public void bulkUpdateStatus(BulkStudentStatusUpdateRequestDto requestDto, UserPrincipal principal) {
        requirePrincipal(principal);
        requireAdmin(principal);
        if (requestDto.getStudentIds() == null || requestDto.getStudentIds().isEmpty()
                || requestDto.getStudentIds().size() > MAX_BULK_SIZE) {
            throw new StudentRequestException("Invalid student batch");
        }
        Long schoolId = resolveSchoolIdForQuery(requestDto.getSchoolId(), principal);
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(requestDto.getStudentIds()));
        List<Student> students = studentRepository.findAllBySchoolIdAndIdIn(schoolId, ids);
        if (students.size() != ids.size()) {
            throw new StudentNotFoundException("Student not found");
        }

        int reactivations = (int) students.stream().filter(s -> s.getStatus() != StudentStatus.ACTIVE
                && requestDto.getStatus() == StudentStatus.ACTIVE).count();
        if (reactivations > 0) planGuard.assertStudentCapacity(schoolId, reactivations);
        Map<Long, StudentStatus> oldStatuses = students.stream()
                .collect(Collectors.toMap(Student::getId, Student::getStatus, (a, b) -> a));
        for (Student student : students) {
            student.setStatus(requestDto.getStatus());
        }

        studentRepository.saveAll(students);
        students.forEach(s -> auditService.record(s.getId(), schoolId, "BULK_STATUS_CHANGE", principal,
                oldStatuses.get(s.getId()) == null ? null : oldStatuses.get(s.getId()).name(), s.getStatus().name()));
        log.info("Bulk updated {} students to status: {} in school ID: {}",
                students.size(), requestDto.getStatus(), schoolId);
    }

    @Transactional
    @Override
    public void deleteStudent(Long id, UserPrincipal principal) {
        requirePrincipal(principal);
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(id)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), id))
                .orElseThrow(() -> new StudentNotFoundException(id));

        validateAdminAccess(student.getSchoolId(), principal);

        student.setStatus(StudentStatus.WITHDRAWN);
        studentRepository.save(student);
        auditService.record(id, student.getSchoolId(), "SOFT_DELETE", principal,
                student.getStatus() == null ? null : student.getStatus().name(), "WITHDRAWN");
        log.info("Soft deleted student ID: {} in school ID: {}", id, student.getSchoolId());
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<StudentSummaryDto> searchStudents(StudentSearchCriteria criteria, Pageable pageable, UserPrincipal principal) {
        requirePrincipal(principal);
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN
                && principal.getRole() != Role.TEACHER) {
            throw new UnauthorizedOperationException("Access denied");
        }
        Long enforcedSchoolId = null;
        if (principal.getRole() != Role.SUPER_ADMIN) {
            enforcedSchoolId = principal.getSchoolId();
        } else if (criteria != null && criteria.getSchoolId() != null) {
            enforcedSchoolId = criteria.getSchoolId();
        } else {
            throw new StudentRequestException("School context is required");
        }

        Specification<Student> spec = StudentSpecification.withCriteria(criteria, enforcedSchoolId);
        Set<String> allowedSorts = Set.of("id", "firstName", "lastName", "admissionNumber", "gradeLevel", "status");
        Pageable safePageable = PageableSortValidator.validate(pageable, allowedSorts);
        Page<StudentSummaryDto> page = studentRepository.findAll(spec, safePageable)
                .map(studentMapper::toSummaryDto);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentStatsDto getStudentStatistics(Long schoolId, UserPrincipal principal) {
        requirePrincipal(principal);
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN) {
            throw new UnauthorizedOperationException("Access denied");
        }
        Long targetSchoolId = resolveSchoolIdForQuery(schoolId, principal);

        School school = schoolRepository.findById(targetSchoolId)
                .orElseThrow(() -> new SchoolNotFoundException(targetSchoolId));

        long totalStudents = studentRepository.countBySchoolId(targetSchoolId);
        long activeStudents = studentRepository.countBySchoolIdAndStatus(targetSchoolId, StudentStatus.ACTIVE);
        long inactiveStudents = totalStudents - activeStudents;

        Map<StudentStatus, Long> statusCounts = new EnumMap<>(StudentStatus.class);
        List<Object[]> groupedStatuses = studentRepository.countGroupedByStatus(targetSchoolId);
        if (groupedStatuses != null) groupedStatuses.forEach(row -> statusCounts.put((StudentStatus) row[0], ((Number) row[1]).longValue()));

        Map<Gender, Long> genderCounts = new EnumMap<>(Gender.class);
        List<Object[]> groupedGenders = studentRepository.countGroupedByGender(targetSchoolId);
        if (groupedGenders != null) groupedGenders.forEach(row -> { if (row[0] != null) genderCounts.put((Gender) row[0], ((Number) row[1]).longValue()); });
        if (genderCounts.isEmpty() && totalStudents > 0) for (Gender gender : Gender.values())
            genderCounts.put(gender, studentRepository.countBySchoolIdAndGender(targetSchoolId, gender));

        // Calculate grade level counts
        Map<String, Long> gradeLevelCounts = new HashMap<>();
        List<Object[]> groupedGrades = studentRepository.countGroupedByGradeLevel(targetSchoolId);
        if (groupedGrades != null) groupedGrades.forEach(row -> gradeLevelCounts.put((String) row[0], ((Number) row[1]).longValue()));
        Map<Long, Long> classLevelCounts = new HashMap<>();
        List<Object[]> groupedClasses = studentRepository.countGroupedByClassLevel(targetSchoolId);
        if (groupedClasses != null) groupedClasses.forEach(row -> {
            if (row[0] != null) classLevelCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        });

        Integer maxCapacity = planGuard.effectiveStudentLimit(school);
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
                .classLevelCounts(classLevelCounts)
                .genderCounts(genderCounts)
                .build();
    }

    @Transactional
    @Override
    public StudentResponseDto linkUserAccount(Long studentId, Long userId, UserPrincipal principal) {
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(studentId)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), studentId))
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateAdminAccess(student.getSchoolId(), principal);
        validateAndLinkUser(userId, student.getSchoolId(), studentId);

        student.setUserId(userId);
        Student savedStudent = studentRepository.saveAndFlush(student);
        auditService.record(studentId, student.getSchoolId(), "USER_LINK", principal, null, String.valueOf(userId));
        log.info("Linked user ID: {} to student ID: {}", userId, studentId);

        return studentMapper.toDto(savedStudent, principal.getRole());
    }

    @Transactional
    @Override
    public StudentResponseDto unlinkUserAccount(Long studentId, UserPrincipal principal) {
        Student student = (principal.getRole() == Role.SUPER_ADMIN
                ? studentRepository.findById(studentId)
                : studentRepository.findBySchoolIdAndId(requireSchool(principal), studentId))
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateAdminAccess(student.getSchoolId(), principal);

        Long oldUserId = student.getUserId();
        student.setUserId(null);
        Student savedStudent = studentRepository.save(student);
        auditService.record(studentId, student.getSchoolId(), "USER_UNLINK", principal, String.valueOf(oldUserId), null);
        log.info("Unlinked user from student ID: {}", studentId);

        return studentMapper.toDto(savedStudent, principal.getRole());
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
            throw new UnauthorizedOperationException("School context is required");
        }

        if (explicitSchoolId != null && !explicitSchoolId.equals(adminSchoolId)) {
            throw new StudentNotFoundException("Student not found");
        }

        return adminSchoolId;
    }

    private Long resolveSchoolIdForQuery(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId != null) {
                return explicitSchoolId;
            }
            throw new StudentRequestException("School context is required");
        }
        Long principalSchoolId = requireSchool(principal);
        if (explicitSchoolId != null && !explicitSchoolId.equals(principal.getSchoolId())) {
            throw new StudentNotFoundException("Student not found");
        }
        return principalSchoolId;
    }

    private void validateAdminAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() != Role.SCHOOL_ADMIN || principal.getSchoolId() == null
                || !schoolId.equals(principal.getSchoolId())) {
            throw new UnauthorizedOperationException("You do not have administrative permission for this school");
        }
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN) {
            throw new UnauthorizedOperationException("Access denied");
        }
    }

    private void validateStudentAccess(Student student, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }

        if (!student.getSchoolId().equals(principal.getSchoolId())) {
            throw new StudentNotFoundException("Student not found");
        }

        if (principal.getRole() == Role.STUDENT) {
            if (student.getUserId() == null || !student.getUserId().equals(principal.getId())) {
                throw new StudentNotFoundException("Student not found");
            }
        }

        if (principal.getRole() == Role.PARENT) {
            boolean matchesEmail = student.getGuardianEmail() != null && principal.getEmail() != null
                    && student.getGuardianEmail().trim().equalsIgnoreCase(principal.getEmail().trim());
            if (!matchesEmail) {
                throw new StudentNotFoundException("Student not found");
            }
        }
    }

    private void validateAndLinkUser(Long userId, Long schoolId, Long currentStudentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found"));

        if (!schoolId.equals(user.getSchoolId())) {
            throw new StudentNotFoundException("Student not found");
        }

        if (user.getRole() != Role.STUDENT) {
            throw new StudentRequestException("Linked user is invalid");
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

    private void validateReferences(Long schoolId, Long classLevelId, Long streamId, Long academicYearId) {
        if (classLevelId != null) {
            ClassLevel level = classLevelRepository.findById(classLevelId)
                    .orElseThrow(() -> new StudentRequestException("Class level not found"));
            if (!schoolId.equals(level.getSchoolId())) {
                throw new StudentRequestException("Class level is not available for this school");
            }
        }
        if (streamId != null) {
            Stream stream = streamRepository.findById(streamId)
                    .orElseThrow(() -> new StudentRequestException("Stream not found"));
            if (!schoolId.equals(stream.getSchoolId())
                    || (classLevelId != null && !classLevelId.equals(stream.getClassLevelId()))) {
                throw new StudentRequestException("Stream is not available for this school/class level");
            }
        }
        if (academicYearId != null) {
            AcademicYear year = academicYearRepository.findById(academicYearId)
                    .orElseThrow(() -> new StudentRequestException("Academic year not found"));
            if (!schoolId.equals(year.getSchoolId())) {
                throw new StudentRequestException("Academic year is not available for this school");
            }
        }
    }

    private void requirePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getRole() == null) {
            throw new UnauthorizedOperationException("Authentication required");
        }
    }

    private Long requireSchool(UserPrincipal principal) {
        if (principal.getSchoolId() == null) {
            throw new UnauthorizedOperationException("School context is required");
        }
        return principal.getSchoolId();
    }

    private Long requireSchoolForAdmin(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            throw new StudentRequestException("School context is required");
        }
        return requireSchool(principal);
    }

}
