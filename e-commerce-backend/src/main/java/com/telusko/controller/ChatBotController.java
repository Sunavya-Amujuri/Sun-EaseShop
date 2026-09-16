package com.telusko.controller;
import com.telusko.service.RagRetrievalService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce/ai")
@RequiredArgsConstructor
public class ChatBotController {

    private final ChatClient chatClient;
    private final RagRetrievalService ragRetrieval;
    private final QueryTransformer queryRewriter;

    @PostMapping("/assistant")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> appAssistant(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody String message
    ) {
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("answer", "Please send a non-empty message."));
        }

        String email = currentUser != null ? currentUser.getUsername() : "anonymous";

        boolean isAdmin = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 1) Rewrite the message into a standalone question first. Short follow-ups like
        //    "and when will it arrive?" carry no meaning on their own, so searching the vector
        //    store with the raw text retrieves noise.
        String searchQuery = queryRewriter.transform(new Query(message)).text();

        // 2) Search in vector store – access rules are applied inside the retrieval service
        List<Document> docs = ragRetrieval.searchForUser(searchQuery, email, isAdmin, 5);

        // 3) Base system prompt (your customer service instructions)
        String systemPrompt = """
                You are a helpful and professional customer service assistant.

                A professional, friendly, and efficient e-commerce customer service chatbot.

                You assist customers by:
                - Searching and managing customer orders if an order number is provided.
                - Answering general e-commerce questions (shipping times, returns, refunds, product availability, payments).
                - Providing clear, helpful, and polite responses to all queries.
                - Offering tracking links, order cancellation, and return help when relevant.

                If not enough information is given, politely ask for more details.

                Privacy rules:
                - Never reveal private data of other users.
                - Only talk about data of the currently logged-in user when the context clearly contains it.
                - If you are not sure about any user-specific detail, say you do not know.

                Response Instructions:
                - Format all responses cleanly and professionally.
                - Do not use any formatting symbols (such as asterisks *, underscores _, or HTML tags like <b>).
                - Use plain text only.
                - When listing multiple items, use dashes (-) or numbers (1., 2., etc.).
                - Keep each section on its own line.
                - Keep responses short, clear, and polite.
                - If the context is not sufficient, politely ask the user to rephrase or provide more information.
                """;

        String context = ragRetrieval.asContext(docs);

        // 4) Either answer from retrieved context, or fall back to general knowledge.
        String userText = context.isEmpty()
                ? message
                : """
                    REFERENCE INFORMATION (internal). Do not mention or refer to this section in your answer.
                    Answer the user's question using it if helpful.

                    REFERENCE START
                    %s
                    REFERENCE END

                    User question: %s
                    """.formatted(context, message);

        String answer = chatClient
                .prompt()
                .system(systemPrompt)
                .user(userText)
                // Chat history is keyed by the logged-in user. Without this every caller shared
                // one default conversation, so one customer's messages became another's context.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, email))
                .call()
                .content();

        return ResponseEntity.ok(Map.of("answer", answer));
    }
}