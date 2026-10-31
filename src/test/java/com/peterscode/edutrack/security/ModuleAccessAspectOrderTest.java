package com.peterscode.edutrack.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.user.entity.Role;
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
    }

    @Test
    void preAuthorizeDeniesBeforeSubscriptionGateLoadsTenantEntitlements() {
        UserPrincipal teacher = UserPrincipal.builder().id(8L).schoolId(7L).role(Role.TEACHER)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null, teacher.getAuthorities()));

        clearInvocations(access);
        assertThatThrownBy(() -> probe.roleOnly()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> probe.read(7L)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(access);
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
        @PreAuthorize("hasRole('SCHOOL_ADMIN')")
        public String read(Long schoolId) {
            return "visible";
        }
    }
}
