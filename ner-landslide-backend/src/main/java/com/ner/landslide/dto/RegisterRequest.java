package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload for POST /api/v1/auth/register. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 100)
    private String username;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "phone is required")
    private String phone;

    private String fullName;

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;

    /** Defaults to CITIZEN in the service layer if omitted. Elevated roles
     *  (DISTRICT_ADMIN, DISASTER_ADMIN, SUPER_ADMIN) should be provisioned
     *  by an existing admin, not via public self-registration. */
    private Role role;

    private String district;
}
