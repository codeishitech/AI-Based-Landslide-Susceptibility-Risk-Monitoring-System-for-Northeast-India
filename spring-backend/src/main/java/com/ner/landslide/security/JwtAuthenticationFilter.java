package com.ner.landslide.security;

import com.ner.landslide.entity.Profile;
import com.ner.landslide.repository.ProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies the Supabase-issued Bearer JWT on every request and, if valid,
 * loads the matching {@code profiles} row so downstream code has the
 * user's role/district available. Replaces the old filter that validated
 * self-issued tokens against a locally-owned {@code users} table.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final SupabaseJwtService supabaseJwtService;
    private final ProfileRepository profileRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER);
        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(PREFIX.length());

        try {
            UUID userId = supabaseJwtService.extractUserId(jwt);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<Profile> profile = profileRepository.findById(userId);
                if (profile.isPresent() && profile.get().isActive()) {
                    Profile p = profile.get();
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + p.getRole().name()));
                    var authToken = new UsernamePasswordAuthenticationToken(p, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else if (profile.isEmpty()) {
                    // Valid Supabase session but no profiles row yet (e.g. the
                    // profiles_insert trigger/flow hasn't run) — treat as anonymous.
                    log.debug("No profile found for authenticated Supabase user {}", userId);
                }
            }
        } catch (Exception ex) {
            // Invalid/expired token: leave SecurityContext empty so the request is treated as anonymous.
            log.debug("Supabase JWT validation failed: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
