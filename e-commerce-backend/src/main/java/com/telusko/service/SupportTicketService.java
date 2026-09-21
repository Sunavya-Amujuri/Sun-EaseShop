package com.telusko.service;

import com.telusko.dto.TicketTriageResult;
import jakarta.persistence.EntityNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.telusko.dto.CreateTicketRequest;
import com.telusko.dto.UpdateTicketStatusRequest;
import com.telusko.enums.OrderStatus;
import com.telusko.enums.TicketStatus;
import com.telusko.model.Order;
import com.telusko.model.SupportMessage;
import com.telusko.model.SupportTicket;
import com.telusko.model.User;
import com.telusko.repository.OrderRepository;
import com.telusko.repository.SupportMessageRepository;
import com.telusko.repository.SupportTicketRepository;
import com.telusko.repository.UserRepository;

@Service
@Transactional
public class SupportTicketService {

    private final SupportTicketRepository ticketRepo;
    private final SupportMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;

    private final AppVectorStoreService appVectors;
    private final TicketTriageService triageService;
    private final ChatClient oneShotChatClient;

    public SupportTicketService(
            SupportTicketRepository ticketRepo,
            SupportMessageRepository messageRepo,
            UserRepository userRepo,
            OrderRepository orderRepo,
            AppVectorStoreService appVectors,
            TicketTriageService triageService,
            @Qualifier("oneShotChatClient")
            ChatClient oneShotChatClient
    ) {
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.appVectors = appVectors;
        this.triageService = triageService;
        this.oneShotChatClient = oneShotChatClient;
    }

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

        // First message = original description
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
    public Page<SupportTicket> getMyTickets(
            String userEmail,
            int page,
            int limit
    ) {

        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(
                page - 1,
                limit,
                Sort.by("id").descending()
        );

        return ticketRepo.findByUser(user, pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getMyTicketById(
            String userEmail,
            Long ticketId
    ) {

        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        SupportTicket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));

        if (!ticket.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You do not own this ticket");
        }

        return ticket;
    }

    public SupportMessage addUserMessage(
            String userEmail,
            Long ticketId,
            String messageContent
    ) {

        if (messageContent == null || messageContent.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        SupportTicket ticket = getMyTicketById(userEmail, ticketId);

        SupportMessage m = SupportMessage.builder()
                .ticket(ticket)
                .fromAdmin(false)
                .content(messageContent.trim())
                .build();

        SupportMessage savedMessage = messageRepo.save(m);

        /*
         * Get the complete conversation after saving
         * the new customer message.
         */
        List<SupportMessage> messages =
                messageRepo.findByTicketOrderByCreatedAtAsc(ticket);

        String conversation = messages.stream()
                .map(msg -> (msg.isFromAdmin() ? "Support: " : "Customer: ")
                        + msg.getContent())
                .collect(Collectors.joining("\n"));

        /*
         * IMPORTANT:
         *
         * Only the latest CUSTOMER message should determine
         * what the customer is asking for now.
         *
         * Previous Support messages must not cause an old
         * return request to be detected again.
         */
        String latestCustomerMessage = messages.stream()
                .filter(msg -> !msg.isFromAdmin())
                .map(SupportMessage::getContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .reduce((first, second) -> second)
                .orElse("");

        /*
         * Determine whether the customer's CURRENT message
         * is a return/refund request.
         *
         * This deliberately does NOT inspect the complete
         * conversation.
         */
        boolean isReturnRequest = latestCustomerMessage
                .toLowerCase()
                .matches("(?s).*\\b(return|returning|refund|send back)\\b.*");

        /*
         * Extract order numbers ONLY from customer messages.
         *
         * This prevents an order number written by Support
         * from overriding the order number supplied by the customer.
         *
         * If the customer mentions multiple order numbers,
         * the latest one mentioned by the customer wins.
         */
        String customerConversation = messages.stream()
                .filter(msg -> !msg.isFromAdmin())
                .map(SupportMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        Pattern orderPattern =
                Pattern.compile("(?i)\\bORD-\\d+\\b");

        Matcher orderMatcher =
                orderPattern.matcher(customerConversation);

        String orderNumber = null;

        while (orderMatcher.find()) {
            orderNumber = orderMatcher.group().toUpperCase();
        }

        /*
         * Debug information.
         */
        System.out.println("========== SUPPORT DEBUG ==========");
        System.out.println("Ticket ID: " + ticketId);

        System.out.println("Latest customer message:");
        System.out.println(latestCustomerMessage);

        System.out.println("Customer conversation:");
        System.out.println(customerConversation);

        System.out.println("Detected order number: " + orderNumber);

        System.out.println("isReturnRequest = " + isReturnRequest);

        System.out.println("Full conversation:");
        System.out.println(conversation);

        System.out.println("===================================");

        String suggestedReply;

        /*
         * =====================================================
         * RETURN / REFUND BRANCH
         * =====================================================
         */
        if (isReturnRequest) {

            System.out.println("========== RETURN BRANCH ==========");

            if (orderNumber != null) {

                Optional<Order> orderOpt =
                        orderRepo.findByOrderNumberAndUser_Email(
                                orderNumber,
                                userEmail
                        );

                if (orderOpt.isPresent()) {

                    Order order = orderOpt.get();

                    System.out.println("Order number: " + orderNumber);
                    System.out.println("Order status: " + order.getStatus());

                    /*
                     * A canceled order cannot be returned.
                     */
                    if (order.getStatus() == OrderStatus.CANCELED) {

                        suggestedReply = """
                                Order %s is canceled and cannot be returned.
                                """.formatted(orderNumber).trim();

                    } else {

                        /*
                         * Valid order and not canceled.
                         */
                        suggestedReply = """
                                We are initiating your return request for order %s. A team member will arrange an inspection and collection of the product based on your return request. Thank you for your patience.
                                """.formatted(orderNumber).trim();
                    }

                } else {

                    /*
                     * The customer supplied an order number,
                     * but it could not be verified for this customer.
                     *
                     * Do NOT falsely say that the return is being initiated.
                     */
                    suggestedReply = """
                            We could not verify order %s. Please check the order number and provide the correct order number so we can assist you with the return.
                            """.formatted(orderNumber).trim();
                }

            } else {

                /*
                 * Customer wants a return but has not provided
                 * an order number.
                 */
                suggestedReply = """
                        Please provide your order number so we can verify the order and assist you with the return.
                        """.trim();
            }

        } else {

            /*
             * =================================================
             * NORMAL SUPPORT MESSAGE
             * =================================================
             *
             * Use the one-shot ChatClient here because the normal
             * ChatClient has ChatMemory and requires a conversationId.
             */
            System.out.println("========== AI BRANCH ==========");

            String systemPrompt = """
                    You are a helpful support assistant for an e-commerce website.

                    You are preparing a suggested reply for a support agent.

                    Use the complete ticket conversation below to understand what
                    the customer currently needs.

                    Important rules:
                    - Pay attention to the customer's latest message.
                    - Use earlier messages only as context.
                    - Do not repeat an old response if the customer has provided new information.
                    - If the customer provides an order number, acknowledge it and use it as context.
                    - Do not invent order details.
                    - Do not claim that a return, refund, replacement, or other action
                      has been completed unless the conversation or system data confirms it.
                    - Do not promise a specific processing time or completion date.
                    - Do not ask the customer for additional details when the conversation
                      already clearly indicates what they need.
                    - Do not refer to return instructions, return policies, help pages,
                      website instructions, links, labels, or procedures unless they are
                      explicitly provided in the ticket information or system data.
                    - Do not invent website pages or instructions.
                    - If the customer's latest message is simply an acknowledgement
                      such as "Ok", "Thank you", or "Thanks", respond naturally and
                      briefly. Do not repeat an earlier support action.
                    - Write a concise, natural response that the support agent can send.
                    - Plain text only.
                    """;

            suggestedReply = oneShotChatClient
                    .prompt()
                    .system(systemPrompt)
                    .user("""
                            Ticket subject:
                            %s

                            Ticket description:
                            %s

                            Latest customer message:
                            %s

                            Conversation:
                            %s

                            Write the next appropriate response to the customer's
                            latest message.
                            """.formatted(
                            ticket.getSubject(),
                            ticket.getDescription(),
                            latestCustomerMessage,
                            conversation
                    ))
                    .call()
                    .content();
        }

        /*
         * Save the newly generated suggested reply to the ticket.
         */
        ticket.setSuggestedReply(suggestedReply);
        ticketRepo.save(ticket);

        /*
         * Update the ticket's vector representation.
         */
        appVectors.indexTicket(ticket);

        return savedMessage;
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> getMyTicketMessages(
            String userEmail,
            Long ticketId
    ) {

        SupportTicket ticket = getMyTicketById(userEmail, ticketId);

        return messageRepo.findByTicketOrderByCreatedAtAsc(ticket);
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> getAllTickets(
            int page,
            int limit,
            String status
    ) {

        Pageable pageable = PageRequest.of(
                page - 1,
                limit,
                Sort.by("id").descending()
        );

        if (status != null && !status.isBlank()) {

            try {

                TicketStatus st =
                        TicketStatus.valueOf(status.toUpperCase());

                return ticketRepo.findByStatus(st, pageable);

            } catch (IllegalArgumentException e) {

                throw new IllegalArgumentException(
                        "Invalid ticket status. Allowed values: OPEN, IN_PROGRESS, RESOLVED, CLOSED"
                );
            }
        }

        return ticketRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getTicketById(Long ticketId) {

        return ticketRepo.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
    }

    public SupportMessage addAdminMessage(
            Long ticketId,
            String messageContent
    ) {

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

    public SupportTicket updateStatus(
            Long ticketId,
            UpdateTicketStatusRequest req
    ) {

        SupportTicket ticket = getTicketById(ticketId);

        if (req.getStatus() != null) {
            ticket.setStatus(req.getStatus());
        }

        if (req.getAssignedToEmail() != null
                && !req.getAssignedToEmail().isBlank()) {

            ticket.setAssignedToEmail(
                    req.getAssignedToEmail().trim()
            );
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