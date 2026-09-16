package com.telusko.controller;
import com.telusko.tools.AnalyticsTools;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce/ai/admin")
@RequiredArgsConstructor
public class AdminAnalyticsAssistantController {

    private final ChatClient chatClient;
    private final AnalyticsTools analyticsTools;

    @PostMapping("/analytics-assistant")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal UserDetails admin,
            @RequestBody String userMessage
    ) {
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("response", "Please send a non-empty question."));
        }

        String adminEmail = (admin != null) ? admin.getUsername() : "admin@gmail.com";

        String systemPrompt = String.format("""
            You are a business analytics assistant for an e-commerce store's admin team.
            You can:
            - Report sales summaries (order count, revenue, average order value) for today, week, month, year or all time
            - List best selling products for a period
            - List products that are low on stock and need restocking
            - Show how many orders are sitting in each status
            - Break revenue down by product category

            Current admin email: '%s'

            Rules:
            - Always use the tools to get numbers. Never estimate, guess or calculate figures yourself.
            - State the period you reported on, so the admin knows what the number covers.
            - Amounts are in Indian Rupees (INR).
            - If a tool returns no rows, say plainly that there is no data for that period.
            - Point out anything that looks like it needs action, such as products about to run out
              or a large number of orders stuck in one status.
            - If the question is not about store data, politely say you can only help with store analytics.

            Response Instructions:
            - Use plain text only. Do not use asterisks, underscores or HTML tags.
            - When listing multiple items, use dashes (-) or numbers (1., 2., etc.).
            - Keep each item on its own line and keep the answer short.
            """, adminEmail);

        String answer = chatClient
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(analyticsTools)
                // Keyed per admin so two people using the back office do not share a conversation.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "admin-analytics:" + adminEmail))
                .call()
                .content();

        return ResponseEntity.ok(Map.of("response", answer));
    }
}