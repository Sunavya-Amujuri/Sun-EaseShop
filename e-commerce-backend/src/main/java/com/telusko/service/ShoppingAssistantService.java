package com.telusko.service;

import com.telusko.dto.ProductResponseDto;
import com.telusko.dto.ProductSuggestionResponse;
import com.telusko.dto.ReturnEligibilityResponse;
import com.telusko.enums.OrderStatus;
import com.telusko.model.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telusko.repository.CartRepository;
import com.telusko.repository.OrderRepository;
import com.telusko.repository.ProductRepository;
import com.telusko.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The customer-facing AI features: asking questions about a product, getting recommendations,
 * cart suggestions, and finding out whether an order can be returned.
 * <p>
 * They all follow the same rule: facts come from the database or the vector store, and the model
 * only does the wording. Recommending a product that does not exist, or telling a customer a
 * refund is coming when the policy says otherwise, is worse than showing nothing.
 */
@Service
@Slf4j
public class ShoppingAssistantService {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final CartRepository carts;
    private final UserRepository users;
    private final RagRetrievalService ragRetrieval;
    private final ProductService productService;

    /**
     * Memory-free client: every call here is self-contained and grounded in supplied facts.
     */
    private final ChatClient chatClient;

    public ShoppingAssistantService(ProductRepository products,
                                    OrderRepository orders,
                                    CartRepository carts,
                                    UserRepository users,
                                    RagRetrievalService ragRetrieval,
                                    ProductService productService,
                                    @Qualifier("oneShotChatClient") ChatClient chatClient) {
        this.products = products;
        this.orders = orders;
        this.carts = carts;
        this.users = users;
        this.ragRetrieval = ragRetrieval;
        this.productService = productService;
        this.chatClient = chatClient;
    }

    /**
     * How long after placing an order a return can still be requested.
     */
    private static final int RETURN_WINDOW_DAYS = 7;

    private static final String PLAIN_TEXT_RULES = """
            Response rules:
            - Plain text only. No asterisks, underscores, markdown or HTML tags.
            - Keep it short: three sentences at most unless listing items.
            - Be honest. If the information given does not answer the question, say so plainly.
            - Never invent prices, stock levels, delivery dates, specifications or policies.
            """;

    @Transactional(readOnly = true)
    public String askAboutProduct(Long productId, String question) {
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));

        // The product's own record is the primary source; similar items add useful comparison
        // context for questions like "is there something cheaper?".
        String similar = ragRetrieval.asContext(
                ragRetrieval.searchCatalogExcluding(question, List.of(productId), 3));

        String productFacts = """
                Name: %s
                Description: %s
                Category: %s
                Price: %s
                In stock: %s (%d available)
                """.formatted(
                product.getName(),
                product.getDescription() == null ? "(none provided)" : product.getDescription(),
                product.getCategory() != null ? product.getCategory().getName() : "Uncategorised",
                product.getPrice(),
                product.getStockQty() != null && product.getStockQty() > 0 ? "yes" : "no",
                product.getStockQty() == null ? 0 : product.getStockQty());

        String systemPrompt = """
                You help a shopper decide about one specific product in an online store.
                
                Answer only from the product details provided. The listing is short, so many
                questions genuinely cannot be answered from it - when that happens, say the
                listing does not mention it and suggest contacting support. Do not guess
                specifications.
                
                %s
                """.formatted(PLAIN_TEXT_RULES);

        String userPrompt = """
                PRODUCT THE SHOPPER IS ASKING ABOUT
                %s
                
                OTHER ITEMS IN THE CATALOG (only mention these if the shopper asks for alternatives)
                %s
                
                Shopper's question: %s
                """.formatted(productFacts, similar.isEmpty() ? "(none)" : similar, question);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    private User requireUser(String userEmail) {
        return users.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));
    }


    private List<Product> resolveProducts(List<Document> matches) {
        List<Long> ids = matches.stream()
                .map(doc -> doc.getMetadata().get("productId"))
                .filter(java.util.Objects::nonNull)
                .map(value -> Long.parseLong(String.valueOf(value)))
                .distinct()
                .toList();

        if (ids.isEmpty()) return List.of();

        return products.findActiveByIdWithCategory(ids);
    }

    private List<ProductResponseDto> toDtos(List<Product> list) {
        return list.stream().map(productService::toDto).toList();
    }

    private String describe(List<Product> list) {
        return list.stream()
                .map(p -> "- %s (%s, price %s)".formatted(
                        p.getName(),
                        p.getCategory() != null ? p.getCategory().getName() : "Uncategorised",
                        p.getPrice()))
                .collect(Collectors.joining("\n"));
    }
    private String writeSuggestionNote(String feature, String userPrompt) {
        String systemPrompt = """
                You write a one or two sentence note introducing product suggestions in an online
                store. Only mention the items listed. Never invent items, prices or claims.

                %s
                """.formatted(PLAIN_TEXT_RULES);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.error("Suggestion note generation failed: {}", ex.getMessage());
            return "Here are some items you might like.";
        }
    }


    @Transactional(readOnly = true)
    public ProductSuggestionResponse recommendForUser(String userEmail, int limit) {
        User user = requireUser(userEmail);

        Page<Order> recentOrders = orders.findByUser(user,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "placedAt")));

        List<String> purchasedNames = new ArrayList<>();
        Set<Long> purchasedProductIds = new LinkedHashSet<>();

        for (Order order : recentOrders.getContent()) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                purchasedNames.add(item.getProductName());
                if (item.getProduct() != null) {
                    purchasedProductIds.add(item.getProduct().getId());
                }
            }
        }

        if (purchasedNames.isEmpty()) {
            return new ProductSuggestionResponse(
                    "You have no past orders yet, so there is nothing to base recommendations on. "
                            + "Browse the catalog and we will personalise this once you order.",
                    List.of());
        }

        // The purchase history becomes the search query: semantically nearest catalog items are
        // the ones related to what they already buy.
        String tasteProfile = String.join(", ", purchasedNames);

        List<Product> candidates = resolveProducts(
                ragRetrieval.searchCatalogExcluding(tasteProfile, List.copyOf(purchasedProductIds), limit));

        if (candidates.isEmpty()) {
            return new ProductSuggestionResponse(
                    "Nothing new to suggest right now - you already have what matches your past orders.",
                    List.of());
        }

        String message = writeSuggestionNote("recommendations", """
                The shopper has previously bought: %s

                Recommend these items to them, briefly explaining the connection to what they
                already bought. Mention items by name only.

                ITEMS TO RECOMMEND
                %s
                """.formatted(tasteProfile, describe(candidates)));

        return new ProductSuggestionResponse(message, toDtos(candidates));
    }

    @Transactional(readOnly = true)
    public ProductSuggestionResponse suggestForCart(String userEmail, int limit) {
        User user = requireUser(userEmail);

        Cart cart = carts.findByUser(user).orElse(null);

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return new ProductSuggestionResponse(
                    "Your cart is empty, so there is nothing to suggest alongside it yet.",
                    List.of());
        }

        List<String> cartNames = new ArrayList<>();
        List<Long> cartProductIds = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            cartNames.add(item.getProduct().getName());
            cartProductIds.add(item.getProduct().getId());
        }

        String cartProfile = String.join(", ", cartNames);

        List<Product> candidates = resolveProducts(
                ragRetrieval.searchCatalogExcluding(cartProfile, cartProductIds, limit));

        if (candidates.isEmpty()) {
            return new ProductSuggestionResponse(
                    "Nothing else in the catalog pairs closely with what is in your cart.",
                    List.of());
        }

        String message = writeSuggestionNote("cart-suggestions", """
                The shopper's cart currently contains: %s

                Suggest these items as things that go well with the cart contents. Be brief and
                do not pressure them. Mention items by name only.

                ITEMS TO SUGGEST
                %s
                """.formatted(cartProfile, describe(candidates)));

        return new ProductSuggestionResponse(message, toDtos(candidates));
    }

    private long daysSincePlaced(Order order) {
        if (order.getPlacedAt() == null) return 0;
        return Duration.between(order.getPlacedAt(), LocalDateTime.now()).toDays();
    }

    @Transactional(readOnly = true)
    public ReturnEligibilityResponse checkReturnEligibility(String userEmail, Long orderId) {
        User user = requireUser(userEmail);

        Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new EntityNotFoundException("Order not found with id: " + orderId);
        }

        OrderStatus status = order.getStatus();
        boolean eligible = false;
        String reason;

        // Plain policy, evaluated from real order state. Deliberately readable rather than clever,
        // because this is the part a customer will quote back at support.
        if (status == OrderStatus.DELIVERED) {
            long daysSince = daysSincePlaced(order);
            if (daysSince <= RETURN_WINDOW_DAYS) {
                eligible = true;
                reason = "Delivered %d day(s) ago, within the %d day return window."
                        .formatted(daysSince, RETURN_WINDOW_DAYS);
            } else {
                reason = "Delivered %d day(s) ago, which is past the %d day return window."
                        .formatted(daysSince, RETURN_WINDOW_DAYS);
            }
        } else if (status == OrderStatus.RETURN_REQUEST) {
            reason = "A return has already been requested for this order and is being processed.";
        } else if (status == OrderStatus.RETURNED || status == OrderStatus.REFUNDED) {
            reason = "This order has already been returned and refunded.";
        } else if (status == OrderStatus.CANCELED) {
            reason = "This order was cancelled, so there is nothing to return.";
        } else if (status == OrderStatus.SHIPPED) {
            reason = "This order is still in transit. A return can be requested once it is delivered.";
        } else {
            reason = "This order has not shipped yet (status %s), so it can be cancelled rather than returned."
                    .formatted(status);
        }

        String explanation = explainEligibility(order, eligible, reason);

        return new ReturnEligibilityResponse(
                order.getId(),
                status != null ? status.name() : "UNKNOWN",
                eligible,
                reason,
                explanation);
    }

    private String explainEligibility(Order order, boolean eligible, String reason) {
        String systemPrompt = """
                You explain a return decision that has already been made by the store's system.

                The decision is final and you must not contradict it, soften it or hint that it
                might change. Do not promise refunds, timelines or goodwill gestures. Just explain
                the decision clearly and say what the customer can do next.

                %s
                """.formatted(PLAIN_TEXT_RULES);

        String userPrompt = """
                Order number: %s
                Current status: %s
                Return allowed: %s
                Reason from the system: %s

                Write the message the customer will read.
                """.formatted(
                order.getOrderNumber(),
                order.getStatus(),
                eligible ? "YES" : "NO",
                reason);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            // The decision itself is already computed, so degrade to the plain reason rather than
            // failing the request.
            log.error("Return eligibility explanation failed for order {}: {}",
                    order.getId(), ex.getMessage());
            return reason;
        }
    }
}
