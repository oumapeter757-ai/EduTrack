package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.mapper.SchoolMapper;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private com.peterscode.edutrack.security.AuthRateLimiter authRateLimiter;

    @Spy
    private SchoolMapper schoolMapper = new SchoolMapper();

    @Spy
    private SubscriptionTermsResolver subscriptionTermsResolver = new SubscriptionTermsResolver();

    @InjectMocks
    private SchoolServiceImpl schoolService;

    private School sampleSchool;
    private CreateSchoolRequestDto createDto;
    private UserPrincipal schoolAdminPrincipal;
    private UserPrincipal superAdminPrincipal;

    @BeforeEach
    void setUp() {
        schoolAdminPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("admin@greenwood.edu")
                .schoolId(10L)
                .role(Role.SCHOOL_ADMIN)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")))
                .active(true)
                .build();
        superAdminPrincipal = UserPrincipal.builder()
                .id(2L)
                .email("super@edutrack.com")
                .role(Role.SUPER_ADMIN)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
                .active(true)
                .build();

        sampleSchool = School.builder()
                .name("Greenwood High")
                .code("GWH")
                .subdomain("greenwood")
                .phone("+1234567890")
                .email("info@greenwood.edu")
                .city("Springfield")
                .country("USA")
                .timezone("America/New_York")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.PREMIUM)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(new HashSet<>(Set.of(ServiceModule.ATTENDANCE, ServiceModule.GRADING, ServiceModule.BILLING)))
                .active(true)
                .build();
        sampleSchool.setId(10L);

        createDto = CreateSchoolRequestDto.builder()
                .name("Greenwood High")
                .code("GWH")
                .subdomain("greenwood")
                .email("info@greenwood.edu")
                .phone("+1234567890")
                .subscriptionTier(SubscriptionTier.PREMIUM)
                .subscriptionEndDate(LocalDate.now().plusDays(30))
                .build();
    }

    @Test
    @DisplayName("createSchool succeeds when code and subdomain are unique")
    void createSchool_Success() {
        when(schoolRepository.existsByCode("GWH")).thenReturn(false);
        when(schoolRepository.existsBySubdomain("greenwood")).thenReturn(false);
        when(schoolRepository.saveAndFlush(any(School.class))).thenAnswer(invocation -> {
            School school = invocation.getArgument(0);
            school.setId(10L);
            return school;
        });

        SchoolResponseDto response = schoolService.createSchool(createDto);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCode()).isEqualTo("GWH");
        assertThat(response.getSubdomain()).isEqualTo("greenwood");
        assertThat(response.getSubscriptionTier()).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(response.getEnabledModules()).contains(ServiceModule.ATTENDANCE, ServiceModule.GRADING, ServiceModule.BILLING);
        verify(schoolRepository).saveAndFlush(any(School.class));
    }

    @Test
    @DisplayName("createSchool throws DuplicateResourceException when code already exists")
    void createSchool_DuplicateCode_ThrowsException() {
        when(schoolRepository.existsByCode("GWH")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.createSchool(createDto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("School already exists with code: 'GWH'");

        verify(schoolRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchool throws DuplicateResourceException when subdomain already exists")
    void createSchool_DuplicateSubdomain_ThrowsException() {
        when(schoolRepository.existsByCode("GWH")).thenReturn(false);
        when(schoolRepository.existsBySubdomain("greenwood")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.createSchool(createDto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("School already exists with subdomain: 'greenwood'");

        verify(schoolRepository, never()).save(any());
    }

    @Test
    @DisplayName("getSchoolById returns SchoolResponseDto when found")
    void getSchoolById_Success() {
        when(schoolRepository.findById(10L)).thenReturn(Optional.of(sampleSchool));

        SchoolResponseDto response = schoolService.getSchoolById(10L, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Greenwood High");
    }

    @Test
    @DisplayName("getSchoolById throws SchoolNotFoundException when not found")
    void getSchoolById_NotFound_ThrowsException() {
        when(schoolRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.getSchoolById(999L, superAdminPrincipal))
                .isInstanceOf(SchoolNotFoundException.class)
                .hasMessageContaining("School not found with id: '999'");
    }

    @Test
    @DisplayName("getPublicSchoolBySubdomain returns PublicSchoolProfileDto")
    void getPublicSchoolBySubdomain_Success() {
        when(schoolRepository.findBySubdomain("greenwood")).thenReturn(Optional.of(sampleSchool));

        PublicSchoolProfileDto response = schoolService.getPublicSchoolBySubdomain("greenwood");

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Greenwood High");
        assertThat(response.getSubdomain()).isEqualTo("greenwood");
        assertThat(response.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("updateSchoolProfile updates contact and branding info")
    void updateSchoolProfile_Success() {
        when(schoolRepository.findById(10L)).thenReturn(Optional.of(sampleSchool));
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSchoolProfileRequestDto profileDto = UpdateSchoolProfileRequestDto.builder()
                .name("Greenwood Academy")
                .city("Chicago")
                .timezone("America/Chicago")
                .build();

        SchoolResponseDto response = schoolService.updateSchoolProfile(10L, profileDto, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Greenwood Academy");
        assertThat(response.getCity()).isEqualTo("Chicago");
        assertThat(response.getTimezone()).isEqualTo("America/Chicago");
        verify(schoolRepository).save(sampleSchool);
    }

    @Test
    @DisplayName("updateSchoolSubscription updates subscription tier and module entitlements")
    void updateSchoolSubscription_Success() {
        when(schoolRepository.findById(10L)).thenReturn(Optional.of(sampleSchool));
        when(schoolRepository.saveAndFlush(any(School.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSchoolSubscriptionRequestDto subDto = UpdateSchoolSubscriptionRequestDto.builder()
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(Set.of(ServiceModule.LMS, ServiceModule.TRANSPORT, ServiceModule.BILLING))
                .maxStudents(5000)
                .maxTeachers(200)
                .subscriptionEndDate(LocalDate.now().plusDays(30))
                .build();

        SchoolResponseDto response = schoolService.updateSchoolSubscription(10L, subDto);

        assertThat(response).isNotNull();
        assertThat(response.getSubscriptionTier()).isEqualTo(SubscriptionTier.ENTERPRISE);
        assertThat(response.getMaxStudents()).isEqualTo(5000);
        assertThat(response.getEnabledModules()).contains(ServiceModule.LMS, ServiceModule.TRANSPORT);
    }

    @Test
    @DisplayName("toggleSchoolStatus activates or deactivates a school")
    void toggleSchoolStatus_Success() {
        when(schoolRepository.findById(10L)).thenReturn(Optional.of(sampleSchool));

        schoolService.toggleSchoolStatus(10L, false);

        assertThat(sampleSchool.isActive()).isFalse();
        verify(schoolRepository).save(sampleSchool);
    }

    @Test
    @DisplayName("isModuleEnabled checks module entitlement correctly")
    void isModuleEnabled_Success() {
        when(schoolRepository.findById(10L)).thenReturn(Optional.of(sampleSchool));

        assertThat(schoolService.isModuleEnabled(10L, ServiceModule.ATTENDANCE)).isTrue();
        assertThat(schoolService.isModuleEnabled(10L, ServiceModule.LMS)).isFalse();
    }

    @Test
    @DisplayName("getAllSchools with specification filters returns PageResponse")
    void getAllSchools_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(schoolRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleSchool), pageable, 1));

        PageResponse<SchoolResponseDto> response = schoolService.getAllSchools("Greenwood", SubscriptionTier.PREMIUM, true, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Greenwood High");
    }
}
