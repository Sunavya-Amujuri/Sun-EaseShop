package com.telusko.controller;

import com.telusko.service.RagRetrievalService;
import com.telusko.service.ShoppingAssistantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.telusko.model.Order;
import com.telusko.repository.OrderRepository;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce/ai")
public class ChatBotController {

    private final ChatClient chatClient;

    @Qualifier("oneShotChatClient")
    private final ChatClient oneShotChatClient;

    private final RagRetrievalService ragRetrieval;
    private final QueryTransformer queryRewriter;
    private final OrderRepository orders;

    // ADDED
    private final ShoppingAssistantService shoppingAssistantService;

    public ChatBotController(
            ChatClient chatClient,
            @Qualifier("oneShotChatClient") ChatClient oneShotChatClient,
            RagRetrievalService ragRetrieval,
            QueryTransformer queryRewriter,
            OrderRepository orders,

            // ADDED
            ShoppingAssistantService shoppingAssistantService
    ) {
        this.chatClient = chatClient;
        this.oneShotChatClient = oneShotChatClient;
        this.ragRetrieval = ragRetrieval;
        this.queryRewriter = queryRewriter;
        this.orders = orders;

        // ADDED
        this.shoppingAssistantService = shoppingAssistantService;
    }

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

        /*
         * Order-specific questions:
         *
         * If the customer provides an order number, get the current order
         * directly from PostgreSQL. This prevents stale vector-store data
         * or old chat history from overriding the current order status.
         */
        Pattern orderPattern = Pattern.compile("(?i)\\bORD-\\d+\\b");
        Matcher orderMatcher = orderPattern.matcher(message);

        if (orderMatcher.find() && currentUser != null) {

            String orderNumber = orderMatcher.group().toUpperCase();

            Optional<Order> order = orders.findByOrderNumberAndUser_Email(
                    orderNumber,
                    email
            );

            String systemPrompt = """
                    You are a helpful and professional customer service assistant.

                    You are answering a customer about their order.

                    The order information provided below comes directly from the
                    application's current database and is authoritative.

                    IMPORTANT:
                    - Always use the current order status from the database.
                    - Do not replace the current status with information from memory.
                    - Do not invent or change the order status.
                    - Use the database information as the factual source.
                    - You should still answer naturally and conversationally.
                    - If the order is DELIVERED, say it has been delivered.
                    - If the order is CANCELLED, say it has been cancelled.
                    - If the order is CONFIRMED, say it is confirmed.
                    - If the order is RETURN_REQUEST, explain that a return request
                      is currently associated with the order.
                    - If the order is RETURNED or REFUNDED, state that accurately.
                    - If the customer asks for other details, use the supplied
                      database information.

                    Privacy rules:
                    - Only discuss the order belonging to the currently logged-in customer.
                    - Never reveal information about another customer's order.

                    Response Instructions:
                    - Be friendly, professional and natural.
                    - Use plain text only.
                    - Do not use asterisks, underscores, HTML or other formatting symbols.
                    - Keep the answer concise.
                    """;

            String orderContext;

            if (order.isPresent()) {
                Order o = order.get();

                orderContext = """
                        CURRENT DATABASE ORDER DATA

                        Order number: %s
                        Current status: %s
                        Total amount: %s
                        Date placed: %s

                        This information is the current authoritative order data.
                        """.formatted(
                        o.getOrderNumber(),
                        o.getStatus(),
                        o.getTotalAmount(),
                        o.getPlacedAt()
                );
            } else {
                orderContext = """
                        CURRENT DATABASE ORDER DATA

                        No order with order number %s was found for the
                        currently logged-in customer.
                        """.formatted(orderNumber);
            }

            /*
             * Important:
             * Do NOT use RAG or ChatMemory for this order-specific path.
             *
             * This prevents an old vector document or previous conversation
             * from telling the LLM that the order is still CONFIRMED when
             * PostgreSQL says DELIVERED.
             */
            String answer = oneShotChatClient
                    .prompt()
                    .system(systemPrompt)
                    .user("""
                            DATABASE INFORMATION:

                            %s

                            CUSTOMER QUESTION:
                            %s
                            """.formatted(orderContext, message))
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of("answer", answer));
        }


        /*
         * ============================================================
         * ADDED: ALL CUSTOMER ORDERS / ORDER STATUS
         * ============================================================
         *
         * This handles questions such as:
         *
         * "What are the statuses of all my orders?"
         * "Give me all 3 status of my orders"
         * "How many orders do I have?"
         * "Show all my orders"
         *
         * We get the orders directly from PostgreSQL through
         * ShoppingAssistantService instead of using RAG or ChatMemory.
         */
        if (currentUser != null) {

            String lowerMessage = message.toLowerCase();

            boolean isAllOrdersStatusRequest =
                    lowerMessage.contains("all my orders")
                    || lowerMessage.contains("all orders")
                    || lowerMessage.contains("my orders")
                    || lowerMessage.contains("orders status")
                    || lowerMessage.contains("status of my orders")
                    || lowerMessage.contains("status of all")
                    || lowerMessage.contains("all 3")
                    || lowerMessage.contains("how many orders");

            if (isAllOrdersStatusRequest) {

                List<Order> customerOrders =
                        shoppingAssistantService.getCustomerOrders(email);

                String orderContext = customerOrders.stream()
                        .map(o -> """
                                Order number: %s
                                Current status: %s
                                Total amount: %s
                                Date placed: %s
                                """.formatted(
                                o.getOrderNumber(),
                                o.getStatus(),
                                o.getTotalAmount(),
                                o.getPlacedAt()
                        ))
                        .collect(java.util.stream.Collectors.joining("\n"));

                String systemPrompt = """
                        You are a helpful and professional customer service assistant.

                        The order information below comes directly from the
                        application's current database and is authoritative.

                        IMPORTANT:
                        - Use ONLY the order information supplied below.
                        - Never invent an order.
                        - Never change an order's status.
                        - Never use old conversation information to determine order status.
                        - Never use RAG information to determine order status.
                        - Report the exact current status from the database.
                        - Give the total number of orders when the customer asks how many
                          orders they have.

                        Response Instructions:
                        - Be friendly, professional and natural.
                        - Use plain text only.
                        - Do not use asterisks, underscores, HTML or other formatting symbols.
                        - When listing multiple orders, use a numbered list.
                        - Keep the answer concise.
                        """;

                String answer = oneShotChatClient
                        .prompt()
                        .system(systemPrompt)
                        .user("""
                                CURRENT DATABASE ORDER DATA:

                                %s

                                CUSTOMER QUESTION:
                                %s
                                """.formatted(
                                orderContext.isBlank()
                                        ? "No orders found."
                                        : orderContext,
                                message
                        ))
                        .call()
                        .content();

                return ResponseEntity.ok(Map.of("answer", answer));
            }
        }


        /*
         * Everything below this point is the existing general AI flow.
         *
         * Normal questions continue using:
         * QueryTransformer -> RAG -> LLM -> ChatMemory
         */
        boolean isAdmin = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 1) Rewrite the message into a standalone question first.
        String searchQuery = queryRewriter.transform(new Query(message)).text();

        // 2) Search in vector store – access rules are applied inside the retrieval service.
        List<Document> docs = ragRetrieval.searchForUser(
                searchQuery,
                email,
                isAdmin,
                5
        );

        // 3) Base system prompt.
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

        // 4) Existing general AI flow.
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
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, email))
                .call()
                .content();

        return ResponseEntity.ok(Map.of("answer", answer));
    }
}




// import com.telusko.service.RagRetrievalService;
// import io.swagger.v3.oas.annotations.security.SecurityRequirement;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.chat.memory.ChatMemory;
// import org.springframework.ai.document.Document;
// import org.springframework.ai.rag.Query;
// import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.web.bind.annotation.*;
// import com.telusko.model.Order;
// import com.telusko.repository.OrderRepository;

// import java.util.Optional;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// import java.util.List;
// import java.util.Map;

// @RestController
// @RequestMapping("/api/v1/ecommerce/ai")
// public class ChatBotController {

//     private final ChatClient chatClient;
//     @Qualifier("oneShotChatClient")
//     private final ChatClient oneShotChatClient;
//     private final RagRetrievalService ragRetrieval;
//     private final QueryTransformer queryRewriter;
//     private final OrderRepository orders;

//     public ChatBotController(
//         ChatClient chatClient,
//         @Qualifier("oneShotChatClient") ChatClient oneShotChatClient,
//         RagRetrievalService ragRetrieval,
//         QueryTransformer queryRewriter,
//         OrderRepository orders
// ) {
//     this.chatClient = chatClient;
//     this.oneShotChatClient = oneShotChatClient;
//     this.ragRetrieval = ragRetrieval;
//     this.queryRewriter = queryRewriter;
//     this.orders = orders;
// }

//     @PostMapping("/assistant")
//     @SecurityRequirement(name = "bearerAuth")
//     public ResponseEntity<Map<String, String>> appAssistant(
//             @AuthenticationPrincipal UserDetails currentUser,
//             @RequestBody String message
//     ) {
//         if (message == null || message.isBlank()) {
//             return ResponseEntity.badRequest()
//                     .body(Map.of("answer", "Please send a non-empty message."));
//         }

//         String email = currentUser != null ? currentUser.getUsername() : "anonymous";

//         /*
//          * Order-specific questions:
//          *
//          * If the customer provides an order number, get the current order
//          * directly from PostgreSQL. This prevents stale vector-store data
//          * or old chat history from overriding the current order status.
//          */
//         Pattern orderPattern = Pattern.compile("(?i)\\bORD-\\d+\\b");
//         Matcher orderMatcher = orderPattern.matcher(message);

//         if (orderMatcher.find() && currentUser != null) {

//             String orderNumber = orderMatcher.group().toUpperCase();

//             Optional<Order> order = orders.findByOrderNumberAndUser_Email(
//                     orderNumber,
//                     email
//             );

//             String systemPrompt = """
//                     You are a helpful and professional customer service assistant.

//                     You are answering a customer about their order.

//                     The order information provided below comes directly from the
//                     application's current database and is authoritative.

//                     IMPORTANT:
//                     - Always use the current order status from the database.
//                     - Do not replace the current status with information from memory.
//                     - Do not invent or change the order status.
//                     - Use the database information as the factual source.
//                     - You should still answer naturally and conversationally.
//                     - If the order is DELIVERED, say it has been delivered.
//                     - If the order is CANCELLED, say it has been cancelled.
//                     - If the order is CONFIRMED, say it is confirmed.
//                     - If the order is RETURN_REQUEST, explain that a return request
//                       is currently associated with the order.
//                     - If the order is RETURNED or REFUNDED, state that accurately.
//                     - If the customer asks for other details, use the supplied
//                       database information.
                    
//                     Privacy rules:
//                     - Only discuss the order belonging to the currently logged-in customer.
//                     - Never reveal information about another customer's order.

//                     Response Instructions:
//                     - Be friendly, professional and natural.
//                     - Use plain text only.
//                     - Do not use asterisks, underscores, HTML or other formatting symbols.
//                     - Keep the answer concise.
//                     """;

//             String orderContext;

//             if (order.isPresent()) {
//                 Order o = order.get();

//                 orderContext = """
//                         CURRENT DATABASE ORDER DATA

//                         Order number: %s
//                         Current status: %s
//                         Total amount: %s
//                         Date placed: %s

//                         This information is the current authoritative order data.
//                         """.formatted(
//                         o.getOrderNumber(),
//                         o.getStatus(),
//                         o.getTotalAmount(),
//                         o.getPlacedAt()
//                 );
//             } else {
//                 orderContext = """
//                         CURRENT DATABASE ORDER DATA

//                         No order with order number %s was found for the
//                         currently logged-in customer.
//                         """.formatted(orderNumber);
//             }

//             /*
//              * Important:
//              * Do NOT use RAG or ChatMemory for this order-specific path.
//              *
//              * This prevents an old vector document or previous conversation
//              * from telling the LLM that the order is still CONFIRMED when
//              * PostgreSQL says DELIVERED.
//              */
//             String answer = oneShotChatClient
//                     .prompt()
//                     .system(systemPrompt)
//                     .user("""
//                             DATABASE INFORMATION:
                            
//                             %s

//                             CUSTOMER QUESTION:
//                             %s
//                             """.formatted(orderContext, message))
//                     .call()
//                     .content();

//             return ResponseEntity.ok(Map.of("answer", answer));
//         }

//         /*
//          * Everything below this point is the existing general AI flow.
//          *
//          * Normal questions continue using:
//          * QueryTransformer -> RAG -> LLM -> ChatMemory
//          */
//         boolean isAdmin = currentUser != null && currentUser.getAuthorities().stream()
//                 .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

//         // 1) Rewrite the message into a standalone question first.
//         String searchQuery = queryRewriter.transform(new Query(message)).text();

//         // 2) Search in vector store – access rules are applied inside the retrieval service.
//         List<Document> docs = ragRetrieval.searchForUser(
//                 searchQuery,
//                 email,
//                 isAdmin,
//                 5
//         );

//         // 3) Base system prompt.
//         String systemPrompt = """
//                 You are a helpful and professional customer service assistant.

//                 A professional, friendly, and efficient e-commerce customer service chatbot.

//                 You assist customers by:
//                 - Searching and managing customer orders if an order number is provided.
//                 - Answering general e-commerce questions (shipping times, returns, refunds, product availability, payments).
//                 - Providing clear, helpful, and polite responses to all queries.
//                 - Offering tracking links, order cancellation, and return help when relevant.

//                 If not enough information is given, politely ask for more details.

//                 Privacy rules:
//                 - Never reveal private data of other users.
//                 - Only talk about data of the currently logged-in user when the context clearly contains it.
//                 - If you are not sure about any user-specific detail, say you do not know.

//                 Response Instructions:
//                 - Format all responses cleanly and professionally.
//                 - Do not use any formatting symbols (such as asterisks *, underscores _, or HTML tags like <b>).
//                 - Use plain text only.
//                 - When listing multiple items, use dashes (-) or numbers (1., 2., etc.).
//                 - Keep each section on its own line.
//                 - Keep responses short, clear, and polite.
//                 - If the context is not sufficient, politely ask the user to rephrase or provide more information.
//                 """;

//         String context = ragRetrieval.asContext(docs);

//         // 4) Existing general AI flow.
//         String userText = context.isEmpty()
//                 ? message
//                 : """
//                     REFERENCE INFORMATION (internal). Do not mention or refer to this section in your answer.
//                     Answer the user's question using it if helpful.

//                     REFERENCE START
//                     %s
//                     REFERENCE END

//                     User question: %s
//                     """.formatted(context, message);

//         String answer = chatClient
//                 .prompt()
//                 .system(systemPrompt)
//                 .user(userText)
//                 .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, email))
//                 .call()
//                 .content();

//         return ResponseEntity.ok(Map.of("answer", answer));
//     }
 //}