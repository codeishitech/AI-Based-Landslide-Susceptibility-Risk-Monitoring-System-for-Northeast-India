package com.ner.landslide.controller;

import com.ner.landslide.entity.Profile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and login now happen directly against Supabase Auth from
 * the frontend (supabase-js {@code signUp}/{@code signInWithPassword}) —
 * this backend no longer issues or stores credentials. The only auth-
 * related endpoint left here resolves the caller's profile/role from
 * their already-verified Supabase JWT, for clients that want it from
 * the API rather than decoding the token themselves.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Resolves the current Supabase-authenticated user's profile")
public class AuthController {

    @GetMapping("/me")
    @Operation(summary = "Get the profile (role, district, etc.) of the currently authenticated user")
    public ResponseEntity<Profile> me(@AuthenticationPrincipal Profile profile) {
        return ResponseEntity.ok(profile);
    }
}
