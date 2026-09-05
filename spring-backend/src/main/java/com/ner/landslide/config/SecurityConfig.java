package com.ner.landslide.config;

import com.ner.landslide.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security configuration implementing the role-based access
 * control matrix from the README (CITIZEN / FIELD_OFFICER /
 * DISTRICT_ADMIN / DISASTER_ADMIN / SUPER_ADMIN).
 *
 * Credential handling (register/login/password hashing) has moved to
 * Supabase Auth — this service only verifies the Bearer JWT that
 * Supabase issues and enforces authorization on top of it, so there is
 * no local AuthenticationManager/UserDetailsService/PasswordEncoder
 * anymore.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Public read access to zones / alerts / weather / DEM coverage for citizen awareness & map rendering
                        .requestMatchers(HttpMethod.GET, "/api/v1/risk-zones/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/alerts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/weather/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/predictions/coverage", "/api/v1/predictions/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/predictions/predict").permitAll()

                        // Field reports: any authenticated user can submit; officers+ can update
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports", "/api/v1/reports/sync").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/reports/**")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_ADMIN", "DISASTER_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/**").authenticated()
                        .requestMatchers("/api/v1/sync/**").authenticated()

                        // ML integration endpoint: only the trusted ML service / admins should call raw ingestion
                        .requestMatchers(HttpMethod.POST, "/api/v1/predictions")
                        .hasAnyRole("DISASTER_ADMIN", "SUPER_ADMIN")

                        // Zone/alert management: admins only
                        .requestMatchers(HttpMethod.POST, "/api/v1/risk-zones/**")
                        .hasAnyRole("DISTRICT_ADMIN", "DISASTER_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/alerts/**")
                        .hasAnyRole("DISTRICT_ADMIN", "DISASTER_ADMIN", "SUPER_ADMIN")

                        // Analytics: staff roles only
                        .requestMatchers("/api/v1/analytics/**")
                        .hasAnyRole("DISTRICT_ADMIN", "DISASTER_ADMIN", "SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
