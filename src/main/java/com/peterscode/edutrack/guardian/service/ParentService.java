package com.peterscode.edutrack.guardian.service;

import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.guardian.dto.CreateParentRequestDto;
import com.peterscode.edutrack.guardian.dto.LinkChildRequestDto;
import com.peterscode.edutrack.guardian.dto.ParentChildSummary;
import com.peterscode.edutrack.guardian.dto.ParentResponseDto;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.entity.ParentStudentMapping;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.dto.CreateSchoolUserRequestDto;
import com.peterscode.edutrack.user.dto.UserResponseDto;
import com.peterscode.edutrack.user.service.UserRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ParentService {

    private final UserRegistrationService userRegistrationService;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository mappingRepository;
    private final StudentRepository studentRepository;
    private final StreamRepository streamRepository;
    private final ClassLevelRepository classLevelRepository;

    /**
     * Creates a parent account with guardian profile and child links in one transaction.
     */
    @Transactional
    public ParentResponseDto createParentWithChildren(CreateParentRequestDto request, UserPrincipal principal) {
        Long schoolId = resolveSchoolId(principal);

        // Check for duplicate email
        if (request.getEmail() != null && guardianRepository.existsBySchoolIdAndEmail(schoolId, request.getEmail())) {
            throw new DuplicateResourceException("Guardian", "email", request.getEmail());
        }

        // Check for duplicate phone
        if (guardianRepository.existsBySchoolIdAndPhone(schoolId, request.getPhone())) {
            throw new DuplicateResourceException("Guardian", "phone", request.getPhone());
        }

        // Validate student IDs: no duplicates, all exist, same school
        Set<Long> uniqueIds = new HashSet<>(request.getStudentIds());
        if (uniqueIds.size() != request.getStudentIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate student IDs provided");
        }

        List<Student> students = studentRepository.findAllBySchoolIdAndIdIn(schoolId, uniqueIds);
        if (students.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more students not found in this school");
        }

        // Create the user account
        CreateSchoolUserRequestDto userRequest = new CreateSchoolUserRequestDto();
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setUsername(generateUsername(request.getFirstName(), request.getLastName(), request.getEmail()));
        UserResponseDto userResponse = userRegistrationService.createParent(userRequest, principal);

        // Create the guardian profile
        Guardian guardian = Guardian.builder()
                .schoolId(schoolId)
                .userId(userResponse.getId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .alternatePhone(request.getAlternatePhone())
                .relationship(request.getRelationship())
                .occupation(request.getOccupation())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .active(true)
                .build();
        guardian = guardianRepository.save(guardian);

        // Create all parent-student mappings
        LocalDateTime now = LocalDateTime.now();
        for (Student student : students) {
            ParentStudentMapping mapping = ParentStudentMapping.builder()
                    .schoolId(schoolId)
                    .parentId(guardian.getId())
                    .studentId(student.getId())
                    .relationship(request.getRelationship())
                    .primaryFlag(false)
                    .active(true)
                    .linkedBy(principal.getId())
                    .linkedAt(now)
                    .build();
            mappingRepository.save(mapping);
        }

        return toResponseDto(guardian);
    }

    /**
     * Links a child to an existing parent.
     */
    @Transactional
    public ParentStudentMapping linkChild(Long guardianId, LinkChildRequestDto request, UserPrincipal principal) {
        Long schoolId = resolveSchoolId(principal);

        Guardian guardian = guardianRepository.findBySchoolIdAndId(schoolId, guardianId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian", "id", guardianId));

        Student student = studentRepository.findBySchoolIdAndId(schoolId, request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", request.getStudentId()));

        // Check if already linked
        Optional<ParentStudentMapping> existing = mappingRepository
                .findBySchoolIdAndParentIdAndStudentId(schoolId, guardianId, request.getStudentId());
        if (existing.isPresent() && existing.get().isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Child is already linked to this parent");
        }

        ParentStudentMapping mapping;
        if (existing.isPresent()) {
            // Re-activate a previously unlinked child
            mapping = existing.get();
            mapping.setActive(true);
            mapping.setUnlinkedAt(null);
            mapping.setRelationship(request.getRelationship());
        } else {
            mapping = ParentStudentMapping.builder()
                    .schoolId(schoolId)
                    .parentId(guardianId)
                    .studentId(request.getStudentId())
                    .relationship(request.getRelationship())
                    .primaryFlag(false)
                    .active(true)
                    .linkedBy(principal.getId())
                    .linkedAt(LocalDateTime.now())
                    .build();
        }
        return mappingRepository.save(mapping);
    }

    /**
     * Unlinks a child from a parent. Rejects if it's the last active link.
     */
    @Transactional
    public void unlinkChild(Long guardianId, Long studentId, UserPrincipal principal) {
        Long schoolId = resolveSchoolId(principal);

        Guardian guardian = guardianRepository.findBySchoolIdAndId(schoolId, guardianId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian", "id", guardianId));

        studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", studentId));

        ParentStudentMapping mapping = mappingRepository
                .findBySchoolIdAndParentIdAndStudentId(schoolId, guardianId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent-child link", "id", studentId));

        if (!mapping.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Child is not linked to this parent");
        }

        // Reject if this is the last active link
        long activeCount = mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(schoolId, guardianId).size();
        if (activeCount <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot unlink the last child. Deactivate the parent instead.");
        }

        mapping.setActive(false);
        mapping.setUnlinkedAt(LocalDateTime.now());
        mappingRepository.save(mapping);
    }

    /**
     * Returns summaries of all children linked to the current parent.
     */
    @Transactional(readOnly = true)
    public List<ParentChildSummary> myChildren(Long schoolId, UserPrincipal principal) {
        Optional<Guardian> guardian = guardianRepository.findBySchoolIdAndUserId(schoolId, principal.getId());
        if (guardian.isEmpty()) {
            return List.of();
        }

        List<ParentStudentMapping> mappings =
                mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(schoolId, guardian.get().getId());

        Set<Long> studentIds = new HashSet<>();
        for (ParentStudentMapping m : mappings) {
            studentIds.add(m.getStudentId());
        }

        if (studentIds.isEmpty()) {
            return List.of();
        }

        List<Student> students = studentRepository.findAllBySchoolIdAndIdIn(schoolId, studentIds);

        // Load streams and class levels in batches to avoid N+1
        Set<Long> streamIds = new HashSet<>();
        for (Student s : students) {
            if (s.getStreamId() != null) {
                streamIds.add(s.getStreamId());
            }
        }

        Map<Long, Stream> streamMap = new HashMap<>();
        if (!streamIds.isEmpty()) {
            for (Long streamId : streamIds) {
                streamRepository.findBySchoolIdAndId(schoolId, streamId)
                        .ifPresent(s -> streamMap.put(s.getId(), s));
            }
        }

        Set<Long> classLevelIds = new HashSet<>();
        for (Stream s : streamMap.values()) {
            if (s.getClassLevelId() != null) {
                classLevelIds.add(s.getClassLevelId());
            }
        }

        Map<Long, String> classLevelNameMap = new HashMap<>();
        if (!classLevelIds.isEmpty()) {
            for (Long classLevelId : classLevelIds) {
                classLevelRepository.findById(classLevelId)
                        .ifPresent(cl -> classLevelNameMap.put(cl.getId(), cl.getName()));
            }
        }

        List<ParentChildSummary> summaries = new ArrayList<>();
        for (Student student : students) {
            ParentChildSummary summary = ParentChildSummary.builder()
                    .studentId(student.getId())
                    .studentName(student.getFullName())
                    .className(classLevelNameMap.getOrDefault(
                            streamMap.getOrDefault(student.getStreamId(), null),
                            null) == null
                                    ? null
                                    : classLevelNameMap.get(
                                            streamMap.get(student.getStreamId()).getClassLevelId()))
                    .streamName(streamMap.containsKey(student.getStreamId())
                            ? streamMap.get(student.getStreamId()).getName()
                            : null)
                    .status(student.getStatus() != null ? student.getStatus().name() : null)
                    .build();
            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * Gets a guardian by ID for admin access.
     */
    @Transactional(readOnly = true)
    public GuardianResponse getGuardian(Long schoolId, Long guardianId, UserPrincipal principal) {
        Guardian guardian = guardianRepository.findBySchoolIdAndId(schoolId, guardianId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian", "id", guardianId));

        List<ParentStudentMapping> mappings =
                mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(schoolId, guardian.getId());

        return new GuardianResponse(toResponseDto(guardian), mappings.size());
    }

    private ParentResponseDto toResponseDto(Guardian guardian) {
        return ParentResponseDto.builder()
                .id(guardian.getId())
                .firstName(guardian.getFirstName())
                .lastName(guardian.getLastName())
                .email(guardian.getEmail())
                .phone(guardian.getPhone())
                .active(guardian.isActive())
                .build();
    }

    private Long resolveSchoolId(UserPrincipal principal) {
        if (principal.getSchoolId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "School ID is required for SUPER_ADMIN");
        }
        return principal.getSchoolId();
    }

    private String generateUsername(String firstName, String lastName, String email) {
        String base = (firstName + "." + lastName).toLowerCase().replaceAll("[^a-z0-9.]", "");
        if (email != null && !email.isEmpty()) {
            String emailLocal = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9.]", "");
            if (!emailLocal.isEmpty()) {
                base = emailLocal;
            }
        }
        return base.isEmpty() ? "parent" : base;
    }

    public record GuardianResponse(ParentResponseDto guardian, int activeChildrenCount) {}
}
