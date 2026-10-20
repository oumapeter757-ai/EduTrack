package com.peterscode.edutrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration
public class CorsConfig {
    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${edutrack.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;
    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (environment.matchesProfiles("prod") && origins.stream().anyMatch(this::isLocalhost)) {
            log.warn("Localhost CORS origins are enabled while the prod profile is active");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        config.setExposedHeaders(List.of("Authorization", "X-Subscription-Status", "X-Subscription-Days-Left"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private boolean isLocalhost(String origin) {
        return origin.startsWith("http://localhost:") || origin.startsWith("https://localhost:");
    }
}
