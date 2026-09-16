package com.telusko.dto;

import com.telusko.enums.TicketStatus;

import java.util.List;

public record TicketConversationView(
        Long id,
        String subject,
        TicketStatus status,
        String assignedToEmail,
        List<SupportMessageView> messages
) {}