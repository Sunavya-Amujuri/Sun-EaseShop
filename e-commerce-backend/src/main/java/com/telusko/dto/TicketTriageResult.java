package com.telusko.dto;

import com.telusko.enums.TicketCategory;
import com.telusko.enums.TicketPriority;

public record TicketTriageResult(
        TicketCategory category,
        TicketPriority priority,
        String summary,
        String suggestedReply
) {}
