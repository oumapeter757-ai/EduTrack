package com.peterscode.edutrack.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.school.dto.CreateSchoolRequestDto;
import com.peterscode.edutrack.school.dto.UpdateSchoolProfileRequestDto;
import com.peterscode.edutrack.school.dto.UpdateSchoolSubscriptionRequestDto;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SchoolIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    private UserPrincipal superAdminPrincipal;
    private UserPrincipal schoolAdminPrincipal1;
    private UserPrincipal schoolAdminPrincipal2;
    private School savedSchool1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        schoolRepository.deleteAll();

        // Seed Super Admin
        User superAdmin = User.builder()
                .firstName("Super")
                .lastName("Admin")
                .username("superadmin")
                .email("superadmin@edutrack.com")
                .password("Password123!")
                .role(Role.SUPER_ADMIN)
                .schoolId(0L)
                .active(true)
                .build();
        superAdmin = userRepository.save(superAdmin);
        superAdminPrincipal = UserPrincipal.create(superAdmin);

        // Seed School 1
        savedSchool1 = School.builder()
                .name("Alpha Academy")
                .code("ALPHA")
                .subdomain("alpha")
                .phone("+1111111111")
                .email("info@alpha.edu")
                .city("Boston")
                .country("USA")
                .timezone("America/New_York")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.STANDARD)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(Set.of(ServiceModule.STUDENT_MANAGEMENT, ServiceModule.ATTENDANCE, ServiceModule.GRADING))
                .active(true)
                .build();
        savedSchool1 = schoolRepository.save(savedSchool1);

        // Seed School Admin for School 1
        User admin1 = User.builder()
                .firstName("Alpha")
                .lastName("Admin")
                .username("alpha_admin")
                .email("admin@alpha.edu")
                .password("Password123!")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(savedSchool1.getId())
                .active(true)
                .build();
        admin1 = userRepository.save(admin1);
        schoolAdminPrincipal1 = UserPrincipal.create(admin1);

        // Seed School Admin for another School (School 2, ID: 99999L)
        User admin2 = User.builder()
                .firstName("Beta")
                .lastName("Admin")
                .username("beta_admin")
                .email("admin@beta.edu")
                .password("Password123!")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(99999L)
                .active(true)
                .build();
        admin2 = userRepository.save(admin2);
        schoolAdminPrincipal2 = UserPrincipal.create(admin2);
    }

    @Test
    @DisplayName("Super Admin can create a new school successfully")
    void superAdminCanCreateSchool() throws Exception {
        CreateSchoolRequestDto request = CreateSchoolRequestDto.builder()
                .name("Gamma High")
                .code("GAMMA")
                .subdomain("gamma")
                .email("info@gamma.edu")
                .phone("+1234567890")
                .city("Seattle")
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build();

        mockMvc.perform(post("/api/v1/schools")
                        .with(user(superAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Gamma High"))
                .andExpect(jsonPath("$.data.code").value("GAMMA"))
                .andExpect(jsonPath("$.data.subdomain").value("gamma"))
                .andExpect(jsonPath("$.data.subscriptionTier").value("ENTERPRISE"))
                .andExpect(jsonPath("$.data.enabledModules").isArray());
    }

    @Test
    @DisplayName("Duplicate code or subdomain returns 409 Conflict")
    void duplicateCodeReturnsConflict() throws Exception {
        CreateSchoolRequestDto request = CreateSchoolRequestDto.builder()
                .name("Duplicate Alpha")
                .code("ALPHA") // already exists
                .subdomain("alpha-new")
                .build();

        mockMvc.perform(post("/api/v1/schools")
                        .with(user(superAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("School already exists with code: 'ALPHA'"));
    }

    @Test
    @DisplayName("School Admin can update their own school profile")
    void schoolAdminCanUpdateOwnProfile() throws Exception {
        UpdateSchoolProfileRequestDto profileDto = UpdateSchoolProfileRequestDto.builder()
                .name("Alpha Academy International")
                .city("Cambridge")
                .phone("+1999999999")
                .websiteUrl("https://alpha-academy.edu")
                .build();

        mockMvc.perform(put("/api/v1/schools/" + savedSchool1.getId() + "/profile")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Alpha Academy International"))
                .andExpect(jsonPath("$.data.city").value("Cambridge"))
                .andExpect(jsonPath("$.data.code").value("ALPHA")); // Code remains unchanged
    }

    @Test
    @DisplayName("School Admin cannot update another school's profile (Cross-tenant rejection)")
    void schoolAdminCannotUpdateOtherSchoolProfile() throws Exception {
        UpdateSchoolProfileRequestDto profileDto = UpdateSchoolProfileRequestDto.builder()
                .name("Hacked School")
                .build();

        mockMvc.perform(put("/api/v1/schools/" + savedSchool1.getId() + "/profile")
                        .with(user(schoolAdminPrincipal2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("School Admin cannot update subscription tier or entitlements")
    void schoolAdminCannotUpdateSubscription() throws Exception {
        UpdateSchoolSubscriptionRequestDto subDto = UpdateSchoolSubscriptionRequestDto.builder()
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/api/v1/schools/" + savedSchool1.getId() + "/subscription")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Super Admin can update subscription tier, limits and modules")
    void superAdminCanUpdateSubscription() throws Exception {
        UpdateSchoolSubscriptionRequestDto subDto = UpdateSchoolSubscriptionRequestDto.builder()
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .maxStudents(10000)
                .maxTeachers(500)
                .enabledModules(Set.of(ServiceModule.LMS, ServiceModule.BILLING, ServiceModule.TRANSPORT))
                .build();

        mockMvc.perform(put("/api/v1/schools/" + savedSchool1.getId() + "/subscription")
                        .with(user(superAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscriptionTier").value("ENTERPRISE"))
                .andExpect(jsonPath("$.data.maxStudents").value(10000));
    }

    @Test
    @DisplayName("Public subdomain lookup works without authentication")
    void publicSubdomainLookupWorksWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/schools/subdomain/alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Alpha Academy"))
                .andExpect(jsonPath("$.data.subdomain").value("alpha"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("Authenticated user can fetch their current school via /me")
    void authenticatedUserCanFetchCurrentSchool() throws Exception {
        mockMvc.perform(get("/api/v1/schools/me")
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(savedSchool1.getId()))
                .andExpect(jsonPath("$.data.code").value("ALPHA"));
    }

    @Test
    @DisplayName("Super Admin can list schools with search and filter")
    void superAdminCanListSchools() throws Exception {
        mockMvc.perform(get("/api/v1/schools")
                        .param("search", "Alpha")
                        .param("tier", "STANDARD")
                        .param("active", "true")
                        .with(user(superAdminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Super Admin can toggle school status")
    void superAdminCanToggleSchoolStatus() throws Exception {
        mockMvc.perform(put("/api/v1/schools/toggle-status/" + savedSchool1.getId())
                        .param("active", "false")
                        .with(user(superAdminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("School deactivated successfully"));

        mockMvc.perform(get("/api/v1/schools/" + savedSchool1.getId())
                        .with(user(superAdminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }
}
