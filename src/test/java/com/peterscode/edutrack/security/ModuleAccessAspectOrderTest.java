package com.peterscode.edutrack.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.user.entity.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.RequestParam;

@SpringJUnitConfig(ModuleAccessAspectOrderTest.Config.class)
class ModuleAccessAspectOrderTest {
    private final GateProbe probe;
    private final ModuleAccessService access;

    @Autowired
    ModuleAccessAspectOrderTest(GateProbe probe, ModuleAccessService access) {
        this.probe = probe;
        this.access = access;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @org.junit.jupiter.api.BeforeEach
    void clearStartupMockInteractions() {
        clearInvocations(access);
    }

    @Test
    void preAuthorizeDeniesBeforeSubscriptionGateLoadsTenantEntitlements() {
        authenticate(Role.PARENT, 7L);

        assertThatThrownBy(() -> probe.roleOnly()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> probe.read(7L)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(access);
    }

    @Test
    void freeEntitlementsAllowAttendanceButDenyTimetableEvenWhenEnabledModulesAreTampered() {
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, true, List.of(ServiceModule.values())));

        for (Role role : List.of(Role.SCHOOL_ADMIN, Role.TEACHER, Role.STUDENT)) {
            authenticate(role, 7L);
            assertThatThrownBy(() -> probe.read(7L))
                    .isInstanceOf(ModuleAccessDeniedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "MODULE_NOT_IN_PLAN");
            assertThat(probe.attendance()).isEqualTo("attendance");
        }
    }

    @Test
    void basicEntitlementsAllowTimetableAndAttendance() {
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE, true, List.of(ServiceModule.values())));

        for (Role role : List.of(Role.SCHOOL_ADMIN, Role.TEACHER, Role.STUDENT)) {
            authenticate(role, 7L);
            assertThat(probe.read(7L)).isEqualTo("visible");
            assertThat(probe.attendance()).isEqualTo("attendance");
        }
    }

    @Test
    void expiredSubscriptionAllowsReadOnlyRequestsAndDeniesWrites() {
        authenticate(Role.SCHOOL_ADMIN, 7L);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.BASIC, SubscriptionStatus.EXPIRED, true, List.of(ServiceModule.values())));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(probe.read(7L)).isEqualTo("visible");
        request.setMethod("HEAD");
        assertThat(probe.read(7L)).isEqualTo("visible");
        request.setMethod("OPTIONS");
        assertThat(probe.read(7L)).isEqualTo("visible");
        request.setMethod("POST");
        assertThatThrownBy(() -> probe.read(7L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SUBSCRIPTION_EXPIRED");
    }

    @Test
    void schoolUserCannotSelectAnotherSchoolsEntitlements() {
        authenticate(Role.SCHOOL_ADMIN, 7L);

        assertThatThrownBy(() -> probe.read(8L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCESS_DENIED");
        verifyNoInteractions(access);
    }

    @Test
    void inactiveSchoolIsDenied() {
        authenticate(Role.SCHOOL_ADMIN, 7L);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE, false, List.of(ServiceModule.values())));

        assertThatThrownBy(() -> probe.read(7L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SCHOOL_INACTIVE");
    }

    @Test
    void superAdminMustProvideExplicitSchoolIdAndBypassesModuleSubscriptionCheck() {
        authenticate(Role.SUPER_ADMIN, null);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.FREE, SubscriptionStatus.EXPIRED, true, List.of()));

        assertThatThrownBy(() -> probe.read(null))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCESS_DENIED");
        assertThat(probe.read(7L)).isEqualTo("visible");
        verify(access).entitlements(7L);
    }

    private void authenticate(Role role, Long schoolId) {
        UserPrincipal principal = UserPrincipal.builder().id(8L).schoolId(schoolId).role(role)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private SchoolEntitlements entitlements(
            SubscriptionTier tier, SubscriptionStatus status, boolean active, List<ServiceModule> modules) {
        return new SchoolEntitlements(7L, tier, status, LocalDate.now().plusDays(10), ZoneId.of("UTC"),
                java.util.Set.copyOf(modules), ServiceModule.getDefaultModulesForTier(tier), active, 10);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @EnableAspectJAutoProxy
    @Import({ModuleAccessAspect.class, SecurityUtils.class})
    static class Config {
        @Bean
        ModuleAccessService moduleAccessService() {
            return mock(ModuleAccessService.class);
        }

        @Bean
        GateProbe gateProbe() {
            return new GateProbe();
        }
    }

    public static class GateProbe {
        @PreAuthorize("hasRole('SCHOOL_ADMIN')")
        public String roleOnly() {
            return "visible";
        }

        @RequiresModule(ServiceModule.TIMETABLE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STUDENT')")
        public String read(@RequestParam("schoolId") Long schoolId) {
            return "visible";
        }

        @RequiresModule(ServiceModule.ATTENDANCE)
        @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'STUDENT')")
        public String attendance() {
            return "attendance";
        }
    }
}
