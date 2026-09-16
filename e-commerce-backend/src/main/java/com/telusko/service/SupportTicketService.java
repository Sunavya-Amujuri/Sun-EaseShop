package com.telusko.service;

import com.telusko.dto.TicketTriageResult;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telusko.dto.CreateTicketRequest;
import com.telusko.dto.UpdateTicketStatusRequest;
import com.telusko.enums.TicketStatus;
import com.telusko.model.Order;
import com.telusko.model.SupportMessage;
import com.telusko.model.SupportTicket;
import com.telusko.model.User;
import com.telusko.repository.OrderRepository;
import com.telusko.repository.SupportMessageRepository;
import com.telusko.repository.SupportTicketRepository;
import com.telusko.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SupportTicketService {

    private final SupportTicketRepository ticketRepo;
    private final SupportMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;

    private final AppVectorStoreService appVectors;
    private final TicketTriageService triageService;


    public SupportTicket createTicket(String userEmail, CreateTicketRequest req) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Order order = null;
        if (req.getOrderId() != null) {
            order = orderRepo.findById(req.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        }

        SupportTicket ticket = SupportTicket.builder()
                .user(user)
                .order(order)
                .subject(req.getSubject())
                .description(req.getDescription())
                .status(TicketStatus.OPEN)
                .build();
        TicketTriageResult triage = triageService.triage(ticket);
        if (triage != null) {
            ticket.setCategory(triage.category());
            ticket.setPriority(triage.priority());
            ticket.setAiSummary(triage.summary());
            ticket.setSuggestedReply(triage.suggestedReply());
        }



        ticket = ticketRepo.save(ticket);

        // First message = original description (optional but nice)
        appVectors.indexTicket(ticket);
        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            SupportMessage first = SupportMessage.builder()
                    .ticket(ticket)
                    .fromAdmin(false)
                    .content(req.getDescription())
                    .build();
            messageRepo.save(first);
        }

        return ticket;
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> getMyTickets(String userEmail, int page, int limit) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        return ticketRepo.findByUser(user, pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getMyTicketById(String userEmail, Long ticketId) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        SupportTicket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

        if (!ticket.getUser().getId().equals(user.getId())) {
            // This goes to BusinessExceptionHandler → 400 BAD_REQUEST
            throw new IllegalArgumentException("You do not own this ticket");
        }

        return ticket;
    }

    public SupportMessage addUserMessage(String userEmail, Long ticketId, String messageContent) {
        if (messageContent == null || messageContent.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        SupportTicket ticket = getMyTicketById(userEmail, ticketId); // includes ownership check

        SupportMessage m = SupportMessage.builder()
                .ticket(ticket)
                .fromAdmin(false)
                .content(messageContent.trim())
                .build();

        return messageRepo.save(m);
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> getMyTicketMessages(String userEmail, Long ticketId) {
        SupportTicket ticket = getMyTicketById(userEmail, ticketId);
        return messageRepo.findByTicketOrderByCreatedAtAsc(ticket);
    }


    @Transactional(readOnly = true)
    public Page<SupportTicket> getAllTickets(int page, int limit, String status) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());

        if (status != null && !status.isBlank()) {
            try {
                TicketStatus st = TicketStatus.valueOf(status.toUpperCase());
                return ticketRepo.findByStatus(st, pageable);
            } catch (IllegalArgumentException e) {
                // will be caught by BusinessExceptionHandler → 400 BAD_REQUEST
                throw new IllegalArgumentException(
                        "Invalid ticket status. Allowed values: OPEN, IN_PROGRESS, RESOLVED, CLOSED");
            }
        }

        return ticketRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getTicketById(Long ticketId) {
        return ticketRepo.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
    }

    public SupportMessage addAdminMessage(Long ticketId, String messageContent) {
        if (messageContent == null || messageContent.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        SupportTicket ticket = getTicketById(ticketId);

        SupportMessage m = SupportMessage.builder()
                .ticket(ticket)
                .fromAdmin(true)
                .content(messageContent.trim())
                .build();
        appVectors.indexTicket(ticket);

        return messageRepo.save(m);
    }

    public SupportTicket updateStatus(Long ticketId, UpdateTicketStatusRequest req) {
        SupportTicket ticket = getTicketById(ticketId);

        if (req.getStatus() != null) {
            ticket.setStatus(req.getStatus());
        }
        if (req.getAssignedToEmail() != null && !req.getAssignedToEmail().isBlank()) {
            ticket.setAssignedToEmail(req.getAssignedToEmail().trim());
        }

        SupportTicket saved = ticketRepo.save(ticket);
        appVectors.indexTicket(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> getTicketMessagesAdmin(Long ticketId) {
        SupportTicket ticket = getTicketById(ticketId);
        return messageRepo.findByTicketOrderByCreatedAtAsc(ticket);
    }
}
