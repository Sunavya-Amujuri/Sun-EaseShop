package com.telusko.dto;


import java.util.List;

/**
 * A short AI-written note plus the real products it refers to.
 * <p>
 * The products come from the catalog, never from the model, so the customer can only ever be
 * shown items that actually exist and are in stock. The model supplies the wording.
 */
public record ProductSuggestionResponse(
        String message,
        List<ProductResponseDto> products
) {}
