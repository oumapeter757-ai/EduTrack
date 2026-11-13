package com.peterscode.edutrack.config;

import com.peterscode.edutrack.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CorsConfigurationSource corsConfigurationSource;
    private final AuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS with explicit configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // 2. Disable CSRF (stateless JWT API — tokens are not stored in cookies)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Set authorization rules for routes
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/signup",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/notifications/opt-out").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/notifications/opt-out").permitAll()
                        // Public tenant lookup for login screens
                        .requestMatchers("/api/v1/schools/subdomain/**").permitAll()
                        .requestMatchers("/api/v1/schools/code/**").permitAll()
                        // Mpesa C2B webhook callbacks (public — called by Safaricom)
                        .requestMatchers("/api/v1/payments/callback/**").permitAll()
                        // Fees Mpesa webhook callbacks (public — called by Safaricom)
                        .requestMatchers("/api/v1/fees/callback/**").permitAll()
                        // Actuator health check
                        .requestMatchers("/actuator/health").permitAll()
                        // Lock down everything else
                        .anyRequest().authenticated()
                )

                // 4. Stateless session management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 5. Custom JSON error responses for 401 and 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // 6. Security headers
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                )

                // 7. Register authentication provider and filters
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}