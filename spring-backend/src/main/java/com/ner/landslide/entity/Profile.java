package com.ner.landslide.entity;

import com.ner.landslide.entity.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to Supabase's {@code public.profiles} table. The row's {@code id}
 * is the same UUID as {@code auth.users.id} — Supabase Auth owns identity
 * (email/password, sessions, tokens); this table only holds the
 * platform-specific profile data (role, district, contact info) that the
 * backend needs for authorization decisions.
 *
 * This replaces the old self-managed {@code User} entity/table — user
 * accounts and credentials are no longer duplicated here.
 */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    private UUID id; // == auth.users.id / auth.uid()

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Role role = Role.CITIZEN;

    private String district;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
