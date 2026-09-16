package com.telusko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.telusko.enums.Role;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank
    private String fullName;
    @Email
    @NotBlank private String email;
    @NotBlank @Size(min = 6) private String password;
    // Required: User.phoneNumber is a NOT NULL column, so a missing value would
    // otherwise surface as a database error instead of a validation error.
    @NotBlank private String phoneNumber;
    private Role role;
}