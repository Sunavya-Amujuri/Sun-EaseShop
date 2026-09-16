package com.telusko.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTicketRequest {

    @NotBlank
    private String subject;

    @NotBlank
    private String description;

    // optional: link to an order
    private Long orderId;
}
