package com.peterscode.edutrack.auth.service;

import com.peterscode.edutrack.auth.dto.AuthResponseDto;
import com.peterscode.edutrack.auth.dto.LoginRequestDto;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.jwt.JwtService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.shared.exception.SchoolInactiveException;
import com.peterscode.edutrack.shared.exception.SchoolRequiredException;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Authentication service for login only.
 * <p>
 * User creation is handled by {@link com.peterscode.edutrack.user.service.UserRegistrationService}
 * with hierarchical role enforcement. No self-registration is allowed.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SchoolRepository schoolRepository;
    private final ModuleAccessService moduleAccessService;
    private final AuthRateLimiter rateLimiter;

    public AuthResponseDto login(LoginRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();
        rateLimiter.checkLogin(email);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException ex) {
            throw ex;
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // Block login for users whose school has been deactivated (SUPER_ADMIN has no school)
        if (userPrincipal.getRole() != Role.SUPER_ADMIN) {
            if (userPrincipal.getSchoolId() == null) {
                throw new SchoolRequiredException();
            }
            if (!moduleAccessService.isSchoolActive(userPrincipal.getSchoolId())) {
                throw new SchoolInactiveException("Your school account has been deactivated. Please contact support.");
            }
        }

        String jwtToken = jwtService.generateToken(userPrincipal);

        return AuthResponseDto.builder()
                .token(jwtToken)
                .email(userPrincipal.getUsername())
                .role(userPrincipal.getAuthorities().iterator().next().getAuthority())
                .schoolId(userPrincipal.getSchoolId())
                .build();
    }
}