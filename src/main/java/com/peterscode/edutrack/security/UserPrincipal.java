package com.peterscode.edutrack.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class  UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final Long schoolId;
    private final Role role;

    @JsonIgnore
    private final String password;

    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.schoolId = user.getSchoolId();
        this.role = user.getRole();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.active = user.isActive();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /**
     * Factory method to build UserPrincipal directly from your User domain entity.
     */
    public static UserPrincipal create(User user) {
        // Enforce Spring Security authority convention: "ROLE_SUPER_ADMIN", "ROLE_SCHOOL_ADMIN", etc.
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .schoolId(user.getSchoolId())
                .role(user.getRole())
                .password(user.getPassword())
                .authorities(authorities)
                .active(user.isActive())
                .build();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}