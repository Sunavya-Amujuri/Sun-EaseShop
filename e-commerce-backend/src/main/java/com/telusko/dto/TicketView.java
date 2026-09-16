package com.telusko.dto;

import com.telusko.enums.TicketCategory;
import com.telusko.enums.TicketPriority;
import com.telusko.enums.TicketStatus;

public record TicketView(
        Long id,
        String subject,
        TicketStatus status,
        String assignedToEmail,
        // Triage output, so the assistant can answer "what is urgent?" without opening tickets.
        TicketCategory category,
        TicketPriority priority,
        String aiSummary,
        // Admin-only draft reply. This view is only ever returned to admins.
        String suggestedReply
) {}
