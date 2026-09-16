package com.telusko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpRequest {

    @NotBlank @Email
    private String email;

    // Exactly six digits. Rejecting anything else here means a malformed code never reaches the
    // attempt counter, so typos in length cannot burn a customer's remaining tries.
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "must be a 6 digit code")
    private String otp;
}
