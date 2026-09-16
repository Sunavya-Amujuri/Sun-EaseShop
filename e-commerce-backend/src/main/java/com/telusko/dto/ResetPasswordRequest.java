package com.telusko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank @Email
    private String email;

    /** The six digit code from the reset email. */
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "must be a 6 digit code")
    private String otp;

    @NotBlank @Size(min = 6) private String newPassword;
}
