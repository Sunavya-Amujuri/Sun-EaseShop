package com.telusko.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telusko.dto.CartItemResponseDto;
import com.telusko.dto.CartResponseDto;
import com.telusko.model.Cart;
import com.telusko.model.CartItem;
import com.telusko.model.Product;
import com.telusko.model.User;
import com.telusko.repository.CartRepository;
import com.telusko.repository.ProductRepository;
import com.telusko.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository carts;
    private final ProductRepository products;
    private final UserRepository users;
    private final AppVectorStoreService appVectors;

    private Cart getOrCreateCart(User user) {
        Optional<Cart> existing = carts.findByUser(user);
        return existing.orElseGet(() -> carts.save(
                Cart.builder()
                        .user(user)
                        .totalAmount(BigDecimal.ZERO)
                        .build()
        ));
    }

    private void recalcTotal(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            BigDecimal line = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(line);
        }
        cart.setTotalAmount(total);
    }

    private CartResponseDto toDto(Cart cart) {
        List<CartItemResponseDto> items = cart.getItems().stream()
                .map(item -> CartItemResponseDto.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .mainImage(item.getProduct().getMainImage())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getUnitPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return CartResponseDto.builder()
                .items(items)
                .totalAmount(cart.getTotalAmount())
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(cart.getTotalAmount())
                .appliedCouponCode(null)
                .build();
    }

    private User getUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + email));
    }

    private Product getActiveProduct(Long productId) {
        return products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found with id: " + productId));
    }


    public CartResponseDto getUserCart(String userEmail) {
        User user = getUser(userEmail);
        Cart cart = getOrCreateCart(user);
        appVectors.indexCart(cart);

        return toDto(cart);
    }

    public CartResponseDto addOrUpdateItem(String userEmail,
                                           Long productId,
                                           int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        User user = getUser(userEmail);
        Cart cart = getOrCreateCart(user);
        Product product = getActiveProduct(productId);

        CartItem item = cart.getItems().stream()
                .filter(ci -> ci.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);

        if (item == null) {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build();
            cart.getItems().add(item);
        } else {
            item.setQuantity(quantity);
        }

        recalcTotal(cart);

        Cart saved = carts.save(cart);
        appVectors.indexCart(saved);


        return toDto(saved);
    }

    public CartResponseDto removeItem(String userEmail, Long productId) {
        User user = getUser(userEmail);
        Cart cart = getOrCreateCart(user);

        cart.getItems().removeIf(
                ci -> ci.getProduct().getId().equals(productId));

        recalcTotal(cart);

        Cart saved = carts.save(cart);
        appVectors.indexCart(saved);


        return toDto(saved);
    }

    public CartResponseDto clearCart(String userEmail) {
        User user = getUser(userEmail);
        Cart cart = getOrCreateCart(user);
        cart.getItems().clear();
        recalcTotal(cart);

        Cart saved = carts.save(cart);
        appVectors.indexCart(saved);


        return toDto(saved);
    }
}
