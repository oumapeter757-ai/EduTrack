package com.peterscode.edutrack.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SuperAdminFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserPrincipal superAdminPrincipal;
    private UserPrincipal schoolAdminPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        userRepository.deleteAll();

        superAdminPrincipal = UserPrincipal.create(
                User.builder()
                        .id(1L)
                        .email("admin@edutrack.com")
                        .username("admin")
                        .role(Role.SUPER_ADMIN)
                        .schoolId(0L)
                        .active(true)
                        .build()
        );

        schoolAdminPrincipal = UserPrincipal.create(
                User.builder()
                        .id(2L)
                        .email("schooladmin@edutrack.com")
                        .username("schooladmin")
                        .role(Role.SCHOOL_ADMIN)
                        .schoolId(1L)
                        .active(true)
                        .build()
        );
    }

    @Test
    @DisplayName("Initial super admin can sign up when no super admin exists; subsequent signup is rejected")
    void testInitialSignupAndDisable() throws Exception {
        // Step 1: Initial Super Admin signs up
        CreateSuperAdminRequestDto firstAdmin = new CreateSuperAdminRequestDto();
        firstAdmin.setFirstName("First");
        firstAdmin.setLastName("SuperAdmin");
        firstAdmin.setUsername("firstsuper");
        firstAdmin.setEmail("first@edutrack.com");
        firstAdmin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("firstsuper"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.schoolId").value(0));

        assertThat(userRepository.existsByRole(Role.SUPER_ADMIN)).isTrue();

        // Step 2: Second signup attempt must be rejected (disabled)
        CreateSuperAdminRequestDto secondAdmin = new CreateSuperAdminRequestDto();
        secondAdmin.setFirstName("Second");
        secondAdmin.setLastName("Attempt");
        secondAdmin.setUsername("secondsuper");
        secondAdmin.setEmail("second@edutrack.com");
        secondAdmin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated SUPER_ADMIN can register another SUPER_ADMIN via /api/v1/admin/super-admins")
    void testSuperAdminCanRegisterAnotherSuperAdmin() throws Exception {
        CreateSuperAdminRequestDto newAdmin = new CreateSuperAdminRequestDto();
        newAdmin.setFirstName("Another");
        newAdmin.setLastName("Admin");
        newAdmin.setUsername("anotheradmin");
        newAdmin.setEmail("another@edutrack.com");
        newAdmin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/admin/super-admins")
                        .with(user(superAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("anotheradmin"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.schoolId").value(0));

        assertThat(userRepository.existsByUsername("anotheradmin")).isTrue();
    }

    @Test
    @DisplayName("Non-SUPER_ADMIN (e.g. SCHOOL_ADMIN) cannot register a SUPER_ADMIN")
    void testSchoolAdminCannotRegisterSuperAdmin() throws Exception {
        CreateSuperAdminRequestDto newAdmin = new CreateSuperAdminRequestDto();
        newAdmin.setFirstName("Hack");
        newAdmin.setLastName("Admin");
        newAdmin.setUsername("hackadmin");
        newAdmin.setEmail("hack@edutrack.com");
        newAdmin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/admin/super-admins")
                        .with(user(schoolAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/admin/super-admins is forbidden or unauthorized")
    void testUnauthenticatedCannotRegisterSuperAdmin() throws Exception {
        CreateSuperAdminRequestDto newAdmin = new CreateSuperAdminRequestDto();
        newAdmin.setFirstName("Anon");
        newAdmin.setLastName("Admin");
        newAdmin.setUsername("anonadmin");
        newAdmin.setEmail("anon@edutrack.com");
        newAdmin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/admin/super-admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAdmin)))
                .andExpect(status().isForbidden());
    }
}
