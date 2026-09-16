package com.telusko.controller;

import com.telusko.dto.ProductSuggestionResponse;
import com.telusko.dto.ReturnEligibilityResponse;
import com.telusko.service.ShoppingAssistantService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Customer-facing AI endpoints.
 * <p>
 * Everything here is scoped to the signed-in user - the email comes from the JWT, never from the
 * request body, so a customer cannot ask for someone else's recommendations or orders.
 */
@RestController
@RequestMapping("/api/v1/ecommerce/ai")
@RequiredArgsConstructor
public class ShopperAiController {

    private final ShoppingAssistantService shoppingAssistant;

    /**
     * Ask a question about one product, answered from that product's listing.
     */
    @PostMapping("/products/{productId}/ask")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> askAboutProduct(
            @PathVariable Long productId,
            @RequestBody String question
    ) {
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("answer", "Please send a non-empty question."));
        }

        String answer = shoppingAssistant.askAboutProduct(productId, question);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    @GetMapping("/recommendations")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductSuggestionResponse> recommendations(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "4") int limit
    ) {
        return ResponseEntity.ok(
                shoppingAssistant.recommendForUser(currentUser.getUsername(), limit));
    }

    @GetMapping("/cart/suggestions")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductSuggestionResponse> cartSuggestions(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "4") int limit
    ) {
        return ResponseEntity.ok(
                shoppingAssistant.suggestForCart(currentUser.getUsername(), limit));
    }

    @GetMapping("/orders/{orderId}/return-eligibility")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReturnEligibilityResponse> returnEligibility(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                shoppingAssistant.checkReturnEligibility(currentUser.getUsername(), orderId));
    }
}
