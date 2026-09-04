package com.ner.landslide.service;

import com.ner.landslide.dto.AuthResponse;
import com.ner.landslide.dto.LoginRequest;
import com.ner.landslide.dto.RegisterRequest;
import com.ner.landslide.entity.User;
import com.ner.landslide.entity.enums.Role;
import com.ner.landslide.exception.DuplicateResourceException;
import com.ner.landslide.repository.UserRepository;
import com.ner.landslide.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone number already registered: " + request.getPhone());
        }

        // Public self-registration is capped at CITIZEN/FIELD_OFFICER; anything higher
        // must be provisioned by an existing admin through a separate internal workflow.
        Role requestedRole = request.getRole();
        Role assignedRole = (requestedRole == Role.CITIZEN || requestedRole == Role.FIELD_OFFICER)
                ? requestedRole
                : Role.CITIZEN;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .district(request.getDistrict())
                .active(true)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + request.getUsername()));

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .build();
    }
}
