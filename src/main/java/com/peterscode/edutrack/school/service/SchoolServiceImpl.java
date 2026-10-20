package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.mapper.SchoolMapper;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.peterscode.edutrack.shared.PageableSortValidator;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.time.Clock;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {
    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED_STATUS_TRANSITIONS;
    static {
        EnumMap<SubscriptionStatus, Set<SubscriptionStatus>> m = new EnumMap<>(SubscriptionStatus.class);
        m.put(SubscriptionStatus.TRIAL, EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED));
        m.put(SubscriptionStatus.ACTIVE, EnumSet.of(SubscriptionStatus.GRACE, SubscriptionStatus.SUSPENDED, SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED));
        m.put(SubscriptionStatus.GRACE, EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED));
        m.put(SubscriptionStatus.EXPIRED, EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED));
        m.put(SubscriptionStatus.SUSPENDED, EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED));
        m.put(SubscriptionStatus.CANCELLED, EnumSet.noneOf(SubscriptionStatus.class));
        ALLOWED_STATUS_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;
    private final SubscriptionTermsResolver subscriptionTermsResolver;
    private final Clock clock;
    private final AuthRateLimiter authRateLimiter;

    /** Null-safe transition validator. Skips when either side is null or when the status is unchanged. */
    private void validateTransition(SubscriptionStatus current, SubscriptionStatus requested) {
        if (current == null || requested == null || current == requested) return;
        Set<SubscriptionStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(requested)) {
            throw new com.peterscode.edutrack.shared.exception.InvalidSubscriptionException(
                    "Invalid subscription status transition");
        }
    }

    @Transactional
    @Override
    public SchoolResponseDto createSchool(CreateSchoolRequestDto requestDto) {
        String normalizedCode = requestDto.getCode().toUpperCase().trim();
        String normalizedSubdomain = requestDto.getSubdomain().toLowerCase().trim();

        if (schoolRepository.existsByCode(normalizedCode)) {
            throw new DuplicateResourceException("School", "code", requestDto.getCode());
        }
        if (schoolRepository.existsBySubdomain(normalizedSubdomain)) {
            throw new DuplicateResourceException("School", "subdomain", requestDto.getSubdomain());
        }

        SubscriptionTier tier = requestDto.getSubscriptionTier() != null
                ? requestDto.getSubscriptionTier()
                : SubscriptionTier.FREE;
        requestDto.setSubscriptionTier(tier);
        School school = schoolMapper.toEntity(requestDto);
        SubscriptionTermsResolver.SubscriptionTerms terms = subscriptionTermsResolver.resolve(
                tier,
                requestDto.getSubscriptionStartDate(),
                requestDto.getSubscriptionEndDate(),
                school.getTimezone());
        school.setSubscriptionTier(tier);
        school.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        school.setSubscriptionStartDate(terms.start());
        school.setSubscriptionEndDate(terms.end());
        School savedSchool = saveHandlingDuplicate(school);
        log.info("Created new school: {} ({}) with subdomain: {}", savedSchool.getName(), savedSchool.getCode(), savedSchool.getSubdomain());

        return schoolMapper.toDto(savedSchool);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchool(Long id, UpdateSchoolRequestDto requestDto) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));

        if (requestDto.getCode() != null
                && schoolRepository.existsByCodeAndIdNot(requestDto.getCode().toUpperCase().trim(), id)) {
            throw new DuplicateResourceException("School", "code", requestDto.getCode());
        }
        if (requestDto.getSubdomain() != null
                && schoolRepository.existsBySubdomainAndIdNot(requestDto.getSubdomain().toLowerCase().trim(), id)) {
            throw new DuplicateResourceException("School", "subdomain", requestDto.getSubdomain());
        }

        schoolMapper.updateEntityFromDto(requestDto, existing);
        School updatedSchool = saveHandlingDuplicate(existing);
        log.info("Updated school details for ID: {}", id);

        return schoolMapper.toDto(updatedSchool);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchoolProfile(Long id, UpdateSchoolProfileRequestDto requestDto,
                                                 UserPrincipal principal) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        if (principal.getRole() != Role.SUPER_ADMIN
                && !id.equals(principal.getSchoolId())) {
            throw new SchoolNotFoundException(id);
        }
        schoolMapper.updateEntityFromProfileDto(requestDto, existing);
        if (principal.getRole() != Role.SUPER_ADMIN && requestDto.getCurrency() != null) {
            throw new com.peterscode.edutrack.shared.exception.UnauthorizedOperationException(
                    "Only SUPER_ADMIN may change currency");
        }
        return schoolMapper.toDto(schoolRepository.save(existing));
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchoolSubscription(Long id, UpdateSchoolSubscriptionRequestDto requestDto) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));

        SubscriptionTier oldTier = existing.getSubscriptionTier();
        LocalDate oldStartDate = existing.getSubscriptionStartDate();
        LocalDate oldEndDate = existing.getSubscriptionEndDate();
        SubscriptionTier requestedTier = requestDto.getSubscriptionTier();
        SubscriptionTier effectiveTier = requestedTier != null ? requestedTier : oldTier;
        boolean tierChanged = requestedTier != null && requestedTier != oldTier;
        boolean datesProvided = requestDto.getSubscriptionStartDate() != null
                || requestDto.getSubscriptionEndDate() != null;
        validateTransition(existing.getSubscriptionStatus(), requestDto.getSubscriptionStatus());

        if (!tierChanged && !datesProvided) {
            if (effectiveTier == SubscriptionTier.FREE) {
                existing.setSubscriptionStartDate(null);
                existing.setSubscriptionEndDate(null);
            }
        } else {
            if (tierChanged && effectiveTier != SubscriptionTier.FREE
                    && requestDto.getSubscriptionEndDate() == null) {
                throw new com.peterscode.edutrack.shared.exception.InvalidSubscriptionException(
                        "Subscription end date is required when changing to a paid tier");
            }
            SubscriptionTermsResolver.SubscriptionTerms terms =
                    subscriptionTermsResolver.resolve(
                            effectiveTier,
                            requestDto.getSubscriptionStartDate(),
                            requestDto.getSubscriptionEndDate(),
                            existing.getTimezone(), oldStartDate, oldEndDate,
                            existing.getSubscriptionStatus() == SubscriptionStatus.EXPIRED
                                    || existing.getSubscriptionStatus() == SubscriptionStatus.GRACE);
            existing.setSubscriptionStartDate(terms.start());
            existing.setSubscriptionEndDate(terms.end());
        }
        existing.setSubscriptionTier(effectiveTier);
        if (tierChanged) {
            existing.setMaxStudents(effectiveTier == SubscriptionTier.ENTERPRISE ? null : PlanCatalog.maxStudents(effectiveTier));
            existing.setMaxTeachers(effectiveTier == SubscriptionTier.ENTERPRISE ? null : PlanCatalog.maxTeachers(effectiveTier));
            existing.setEnabledModules(enumSet(ServiceModule.getDefaultModulesForTier(effectiveTier)));
        }
        // Service is authoritative for tier/status/dates/limits/modules.
        // Mapper only applies DTO overrides for maxStudents/maxTeachers/enabledModules (A7).
        if (requestDto.getEnabledModules() != null) {
            existing.setEnabledModules(enumSet(requestDto.getEnabledModules()));
        }
        if (requestDto.getMaxStudents() != null) {
            existing.setMaxStudents(requestDto.getMaxStudents());
        }
        if (requestDto.getMaxTeachers() != null) {
            existing.setMaxTeachers(requestDto.getMaxTeachers());
        }
        if (requestDto.getSubscriptionStatus() != null) {
            // Explicit status wins; no auto-reactivation.
            existing.setSubscriptionStatus(requestDto.getSubscriptionStatus());
        } else if ((existing.getSubscriptionStatus() == SubscriptionStatus.EXPIRED
                || existing.getSubscriptionStatus() == SubscriptionStatus.GRACE)
                && requestDto.getSubscriptionEndDate() != null) {
            // Auto-reactivation only when the caller did NOT send a status (A5).
            LocalDate todayInTz;
            try {
                todayInTz = LocalDate.now(clock.withZone(ZoneId.of(existing.getTimezone())));
            } catch (DateTimeException ex) {
                todayInTz = LocalDate.now(clock);
            }
            if (!requestDto.getSubscriptionEndDate().isBefore(todayInTz)) {
                existing.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            }
        }
        if (oldTier != SubscriptionTier.FREE && effectiveTier == SubscriptionTier.FREE) {
            log.info("School ID: {} changed from paid tier {} to FREE; cleared subscription dates {} to {}",
                    id, oldTier, oldStartDate, oldEndDate);
        }
        School updatedSchool = saveHandlingDuplicate(existing);
        log.info("Updated subscription for school ID: {} to tier: {}", id, effectiveTier);

        return schoolMapper.toDto(updatedSchool);
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getSchoolById(Long id, UserPrincipal principal) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        if (principal.getRole() != Role.SUPER_ADMIN
                && !id.equals(principal.getSchoolId())) {
            throw new SchoolNotFoundException(id);
        }
        return schoolMapper.toDto(school);
    }

    @Transactional(readOnly = true)
    @Override
    public PublicSchoolProfileDto getSchoolByCode(String code) {
        authRateLimiter.checkPublicLookup();
        String normalizedCode = normalizeLookupKey(code, "code", 50);
        return schoolRepository.findByCode(normalizedCode.toUpperCase(Locale.ROOT))
                .map(schoolMapper::toPublicDto)
                .orElseThrow(() -> SchoolNotFoundException.byCode(normalizedCode));
    }

    @Transactional(readOnly = true)
    @Override
    public PublicSchoolProfileDto getPublicSchoolBySubdomain(String subdomain) {
        authRateLimiter.checkPublicLookup();
        String normalizedSubdomain = normalizeLookupKey(subdomain, "subdomain", 100);
        return schoolRepository.findBySubdomain(normalizedSubdomain.toLowerCase(Locale.ROOT))
                .map(schoolMapper::toPublicDto)
                .orElseThrow(() -> SchoolNotFoundException.bySubdomain(normalizedSubdomain));
    }

    private String normalizeLookupKey(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("School " + field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("School " + field + " is invalid");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SchoolResponseDto> getAllSchools(String search, SubscriptionTier tier, Boolean active, Pageable pageable) {
        Specification<School> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.toLowerCase().trim()) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("city")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\')
                ));
            }

            if (tier != null) {
                predicates.add(criteriaBuilder.equal(root.get("subscriptionTier"), tier));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Pageable safePageable = PageableSortValidator.validate(pageable,
                Set.of("name", "code", "city", "subscriptionTier", "createdAt"));
        Page<SchoolResponseDto> page = schoolRepository.findAll(spec, safePageable)
                .map(schoolMapper::toDto);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SchoolResponseDto> getAllSchools(Pageable pageable) {
        return getAllSchools(null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getCurrentSchool(Long schoolId) {
        if (schoolId == null) {
            throw new SchoolNotFoundException("Current user has no associated school");
        }
        // Direct repo lookup; the caller already resolved the principal's schoolId,
        // so no tenant check is needed and we avoid routing through the unchecked overload (A8).
        return schoolRepository.findById(schoolId)
                .map(schoolMapper::toDto)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
    }

    @Transactional
    @Override
    public void toggleSchoolStatus(Long id, boolean active) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        school.setActive(active);
        schoolRepository.save(school);
        log.info("School ID: {} active status changed to: {}", id, active);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateEnabledModules(Long id, Set<ServiceModule> modules) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        school.setEnabledModules(enumSet(modules));
        School savedSchool = schoolRepository.save(school);
        log.info("Updated enabled modules for school ID: {} - modules: {}", id, modules);
        return schoolMapper.toDto(savedSchool);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isModuleEnabled(Long schoolId, ServiceModule module) {
        if (schoolId == null || module == null) {
            return false;
        }
        return schoolRepository.findById(schoolId)
                .map(school -> school.isModuleEnabled(module))
                .orElse(false);
    }

    /**
     * Persists the school with {@code saveAndFlush} so unique-constraint violations surface
     * inside the try block (a plain {@code save} on a managed entity would throw at commit).
     * Only violations of {@code uk_school_code} and {@code uk_school_subdomain} are translated
     * to {@link DuplicateResourceException}. Every other violation is rethrown unchanged.
     */
    private School saveHandlingDuplicate(School school) {
        try {
            return schoolRepository.saveAndFlush(school);
        } catch (DataIntegrityViolationException ex) {
            String constraint = extractConstraintName(ex);
            String haystack = (constraint != null ? constraint : safeMessage(ex)).toLowerCase();
            if (haystack.contains("uk_school_code")) {
                throw new DuplicateResourceException("School", "code", school.getCode());
            }
            if (haystack.contains("uk_school_subdomain")) {
                throw new DuplicateResourceException("School", "subdomain", school.getSubdomain());
            }
            throw ex;
        }
    }

    private static String extractConstraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
        }
        return null;
    }

    private static String safeMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private static EnumSet<ServiceModule> enumSet(Set<ServiceModule> modules) {
        EnumSet<ServiceModule> result = EnumSet.noneOf(ServiceModule.class);
        if (modules != null) result.addAll(modules);
        return result;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
