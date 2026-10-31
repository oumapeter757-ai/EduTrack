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
        UserPrincipal teacher = UserPrincipal.builder().id(8L).schoolId(7L).role(Role.TEACHER)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null, teacher.getAuthorities()));

        assertThatThrownBy(() -> probe.roleOnly()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> probe.read(7L)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(access);
    }

    @Test
    void freeEntitlementsAllowAttendanceButDenyTimetableEvenWhenEnabledModulesAreTampered() {
        authenticate(Role.SCHOOL_ADMIN, 7L);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, true, List.of(ServiceModule.values())));

        assertThatThrownBy(() -> probe.read(7L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MODULE_NOT_IN_PLAN");
        assertThat(probe.attendance()).isEqualTo("attendance");
    }

    @Test
    void basicEntitlementsAllowTimetableAndAttendance() {
        authenticate(Role.SCHOOL_ADMIN, 7L);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE, true, List.of(ServiceModule.values())));

        assertThat(probe.read(7L)).isEqualTo("visible");
        assertThat(probe.attendance()).isEqualTo("attendance");
    }

    @Test
    void expiredSubscriptionAllowsReadOnlyRequestsAndDeniesWrites() {
        authenticate(Role.SCHOOL_ADMIN, 7L);
        when(access.entitlements(7L)).thenReturn(entitlements(
                SubscriptionTier.BASIC, SubscriptionStatus.EXPIRED, true, List.of(ServiceModule.values())));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(probe.read(7L)).isEqualTo("visible");
        request.setMethod("POST");
        assertThatThrownBy(() -> probe.read(7L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SUBSCRIPTION_EXPIRED");
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
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
        public String read(@RequestParam("schoolId") Long schoolId) {
            return "visible";
        }

        @RequiresModule(ServiceModule.ATTENDANCE)
        @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
        public String attendance() {
            return "attendance";
        }
    }
}
