package com.telusko.dto;

import lombok.Data;
import com.telusko.enums.TicketStatus;

@Data
public class UpdateTicketStatusRequest {
    private TicketStatus status;
    private String assignedToEmail;
}
