package com.peterscode.edutrack.security.jwt;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.service.CustomUserDetailsService;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.user.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ModuleAccessService moduleAccessService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check if token exists and has correct Bearer prefix
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract token
        jwt = authHeader.substring(7);

        try {
            userEmail = jwtService.extractUsername(jwt);

            // 3. If we have an email and the user is not yet authenticated in this thread
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 4. Validate token against the database record
                if (jwtService.isTokenValid(jwt, userDetails)
                        && userDetails.isEnabled()
                        && userDetails.isAccountNonLocked()
                        && (userDetails instanceof UserPrincipal p
                        && (p.getRole() == Role.SUPER_ADMIN || moduleAccessService.isSchoolActive(p.getSchoolId())))) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 5. Save the valid user in the Security Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException ex) {
            logger.debug("Expired JWT rejected");
        } catch (Exception ex) {
            logger.debug("Invalid JWT rejected: " + ex.getMessage());
        }

        // 6. Continue the filter chain
        filterChain.doFilter(request, response);
    }
}