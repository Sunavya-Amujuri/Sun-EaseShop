package com.telusko.controller;

import com.telusko.dto.CreateTicketRequest;
import com.telusko.dto.UpdateTicketStatusRequest;
import com.telusko.model.SupportMessage;
import com.telusko.model.SupportTicket;
import com.telusko.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.telusko.tools.SupportTicketTools;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Map;


import java.util.List;

@RestController
@RequestMapping("/api/v1/ecommerce/support")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService service;
    private final ChatClient chatClient;
    private final SupportTicketTools tools;

    @PostMapping("/tickets")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportTicket> createTicket(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody CreateTicketRequest request
    ) {
        SupportTicket ticket = service.createTicket(currentUser.getUsername(), request);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/tickets")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<SupportTicket>> getMyTickets(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(
                service.getMyTickets(currentUser.getUsername(), page, limit)
        );
    }

    @GetMapping("/tickets/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportTicket> getMyTicketById(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                service.getMyTicketById(currentUser.getUsername(), id)
        );
    }

    // message body is just plain text:  "hello support..."
    @PostMapping("/tickets/{id}/messages")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportMessage> addUserMessage(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long id,
            @RequestBody String message
    ) {
        SupportMessage msg =
                service.addUserMessage(currentUser.getUsername(), id, message);
        return ResponseEntity.ok(msg);
    }

    @GetMapping("/tickets/{id}/messages")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SupportMessage>> getMyMessages(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                service.getMyTicketMessages(currentUser.getUsername(), id)
        );
    }

    // -------- ADMIN --------

    @GetMapping("/admin/tickets")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<SupportTicket>> getAllTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(service.getAllTickets(page, limit, status));
    }

    @GetMapping("/admin/tickets/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportTicket> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTicketById(id));
    }

    // message body is plain text too
    @PostMapping("/admin/tickets/{id}/messages")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportMessage> addAdminMessage(
            @PathVariable Long id,
            @RequestBody String message
    ) {
        return ResponseEntity.ok(service.addAdminMessage(id, message));
    }

    @PatchMapping("/admin/tickets/{id}/status")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupportTicket> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateTicketStatusRequest request
    ) {
        return ResponseEntity.ok(service.updateStatus(id, request));
    }

    @GetMapping("/admin/tickets/{id}/messages")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SupportMessage>> getMessagesAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTicketMessagesAdmin(id));
    }

    @GetMapping("/admin/tickets/{id}/suggested-reply")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> getSuggestedReply(@PathVariable Long id) {
        SupportTicket ticket = service.getTicketById(id);
        String draft = ticket.getSuggestedReply();
        return ResponseEntity.ok(Map.of("suggestedReply", draft == null ? "" : draft));
    }

    @PostMapping("/ticket-assistant")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal UserDetails admin,
            @RequestBody String userMessage   // this is what admin types in UI
    ) {
        String adminEmail = (admin != null) ? admin.getUsername() : "admin@gmail.com";

        // System instruction for the AI
        String systemPrompt = String.format("""
            You are a helpful support admin assistant for an e-commerce website.
            You can:
            - List tickets by status (OPEN, IN_PROGRESS, RESOLVED, CLOSED)
            - Show ticket details
            - Show full conversation of a ticket
            - Update ticket status or assign it to a support agent
            - Add admin replies to tickets
                
            Current admin email: '%s'
                
            Always explain what you did in simple language and show important fields
            like ticket id, subject, status, and assignedToEmail.
                
            If the user asks something that is NOT related to support tickets, orders or customers,
            politely refuse and say you are only allowed to help with support tickets.
            """, adminEmail);

        String answer = chatClient
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(tools)
                // Keyed per admin, otherwise every admin shares one default conversation and
                // sees each other's history.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "admin-tickets:" + adminEmail))
                .call()
                .content();

        return ResponseEntity.ok(Map.of("response", answer));
    }
}
