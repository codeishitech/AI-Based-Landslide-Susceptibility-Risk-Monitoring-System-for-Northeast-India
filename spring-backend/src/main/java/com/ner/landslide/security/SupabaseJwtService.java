package com.ner.landslide.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

/**
 * Verifies JWTs issued by Supabase Auth (HS256, signed with the Supabase
 * project's JWT secret). This service only ever *validates* tokens — the
 * backend does not issue its own anymore, since Supabase Auth is now the
 * single identity provider (the frontend talks to it directly via
 * supabase-js signUp/signInWithPassword).
 */
@Service
public class SupabaseJwtService {

    @Value("${app.supabase.jwt-secret}")
    private String jwtSecret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Parses and verifies the token's signature/expiry and returns the
     * Supabase user id (the JWT's {@code sub} claim), which matches
     * {@code auth.uid()} / {@code profiles.id} in the database.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or malformed
     */
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("email", String.class);
    }
}
