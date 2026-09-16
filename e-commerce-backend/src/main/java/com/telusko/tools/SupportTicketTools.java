package com.telusko.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import com.telusko.dto.SupportMessageView;
import com.telusko.dto.TicketConversationView;
import com.telusko.dto.TicketView;
import com.telusko.dto.UpdateTicketStatusRequest;
import com.telusko.enums.TicketStatus;
import com.telusko.model.SupportMessage;
import com.telusko.model.SupportTicket;
import com.telusko.service.SupportTicketService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SupportTicketTools {

    private final SupportTicketService supportTicketService;

    @Tool(description = """
            List support tickets. 
            status can be: OPEN, IN_PROGRESS, RESOLVED, CLOSED. 
            If status is null or empty, return all tickets.
            """)
    public List<TicketView> listTickets(String status) {
        Page<SupportTicket> page = supportTicketService.getAllTickets(1, 50, status);
        return page.getContent().stream()
                .map(this::toTicketView)
                .toList();
    }

    @Tool(description = "Get basic information about a support ticket by its id.")
    public TicketView getTicket(Long ticketId) {
        SupportTicket ticket = supportTicketService.getTicketById(ticketId);
        return toTicketView(ticket);
    }

    @Tool(description = """
            Update support ticket status and/or assignedToEmail. 
            status must be one of: OPEN, IN_PROGRESS, RESOLVED, CLOSED.
            """)


    public TicketView updateTicket(Long ticketId, String status, String assignedToEmail) {
        TicketStatus newStatus = (status != null && !status.isBlank())
                ? TicketStatus.valueOf(status.toUpperCase())
                : null;

        var req = new UpdateTicketStatusRequest();
        req.setStatus(newStatus);
        req.setAssignedToEmail(assignedToEmail);

        SupportTicket updated = supportTicketService.updateStatus(ticketId, req);
        return toTicketView(updated);
    }

    @Tool(description = "Get all messages for a given ticket id, including whether each message is from admin or user.")
    public TicketConversationView getConversation(Long ticketId) {
        SupportTicket ticket = supportTicketService.getTicketById(ticketId);
        List<SupportMessage> rawMessages = supportTicketService.getTicketMessagesAdmin(ticketId);

        List<SupportMessageView> messages = rawMessages.stream()
                .map(m -> new SupportMessageView(
                        m.getId(),
                        m.isFromAdmin(),
                        m.getContent(),
                        m.getCreatedAt()
                ))
                .toList();

        return new TicketConversationView(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getStatus(),
                ticket.getAssignedToEmail(),
                messages
        );
    }

    @Tool(description = "Add an admin reply message to a support ticket.")
    public SupportMessageView addAdminReply(Long ticketId, String content) {
        SupportMessage msg = supportTicketService.addAdminMessage(ticketId, content);
        return new SupportMessageView(
                msg.getId(),
                msg.isFromAdmin(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }

    private TicketView toTicketView(SupportTicket t) {
        return new TicketView(
                t.getId(),
                t.getSubject(),
                t.getStatus(),
                t.getAssignedToEmail(),
                t.getCategory(),
                t.getPriority(),
                t.getAiSummary(),
                t.getSuggestedReply()
        );
    }
}
