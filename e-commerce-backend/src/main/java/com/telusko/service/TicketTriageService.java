package com.telusko.service;
import com.telusko.dto.TicketTriageResult;
import com.telusko.model.Order;
import com.telusko.model.SupportTicket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
@Service
@Slf4j
public class TicketTriageService {

    private final ChatClient chatClient;

    // A memory-free client on purpose. Triage is a one-shot classification, so it must not read
    // from or write to any customer's conversation memory.
    public TicketTriageService(@Qualifier("oneShotChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Returns the triage result, or {@code null} when it could not be produced.
     * <p>
     * Ticket creation calls this, so it never throws: a customer must always be able to raise a
     * ticket even when the model is down, rate limited or misconfigured. An untriaged ticket is a
     * minor inconvenience for an admin; a failed submission loses the customer's problem entirely.
     */
    public TicketTriageResult triage(SupportTicket ticket) {
        try {
            Order order = ticket.getOrder();
            String orderContext = (order == null)
                    ? "The customer did not link an order to this ticket."
                    : "Linked order %s, current status %s, total %s."
                    .formatted(order.getOrderNumber(), order.getStatus(), order.getTotalAmount());

            String systemPrompt = """
                    You triage incoming customer support tickets for an e-commerce store.

                    Choose the single best category:
                    - ORDER_ISSUE: wrong item, missing item, order stuck or cannot be placed
                    - DELIVERY: late, lost or damaged-in-transit shipments, address and tracking problems
                    - PAYMENT: failed payments, double charges, payment method problems
                    - RETURN_REFUND: return requests, refund status, exchanges
                    - PRODUCT_QUALITY: item defective, not as described, poor quality
                    - ACCOUNT: login, password, email verification, profile problems
                    - OTHER: anything that does not fit the above

                    Choose a priority:
                    - URGENT: the customer has lost money, or an order is badly wrong and time-critical
                    - HIGH: blocked from completing a purchase, or a delivery is clearly overdue
                    - MEDIUM: a normal problem that needs a reply but is not blocking
                    - LOW: general questions, feedback, minor requests

                    summary: one short sentence restating the problem, so an admin can scan the queue.

                    suggestedReply: a draft reply the admin can send.
                    - Address the customer politely and acknowledge the specific problem.
                    - Say what will happen next.
                    - Never promise a refund, replacement, discount or delivery date as a commitment.
                    - Do not invent order details, tracking numbers or policy that were not given.
                    - Plain text only, no asterisks, underscores or HTML tags.
                    """;

            String userPrompt = """
                    Subject: %s

                    Description: %s

                    %s
                    """.formatted(
                    ticket.getSubject(),
                    ticket.getDescription() == null ? "(no description given)" : ticket.getDescription(),
                    orderContext);

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(TicketTriageResult.class);

        } catch (Exception ex) {
            log.error("Ticket triage failed for subject '{}': {}", ticket.getSubject(), ex.getMessage());
            // The request still succeeds, so this would be invisible in HTTP metrics. Count it, or
            // an outage shows up only as tickets quietly arriving without a category.
            return null;
        }
    }
}