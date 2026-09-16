package com.telusko.service;

import com.telusko.model.*;
import com.telusko.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telusko.dto.OrderDetailDto;
import com.telusko.dto.OrderItemDto;
import com.telusko.dto.OrderSummaryDto;
import com.telusko.dto.PlaceOrderRequest;
import com.telusko.enums.DiscountType;
import com.telusko.enums.OrderStatus;
import com.telusko.enums.PaymentMethod;
import com.telusko.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orders;
    private final UserRepository users;
    private final CartRepository carts;
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final ProductRepository products;

    private final AppVectorStoreService appVectors;


    private User getUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private OrderSummaryDto toSummaryDto(Order order) {
        return OrderSummaryDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .placedAt(order.getPlacedAt())
                .itemsCount(order.getItems() == null ? 0 : order.getItems().size())
                .build();
    }

    private OrderDetailDto toDetailDto(Order order) {
        List<OrderItemDto> items = order.getItems().stream()
                .map(oi -> OrderItemDto.builder()
                        .productId(oi.getProduct().getId())
                        .productName(oi.getProductName() != null
                                ? oi.getProductName()
                                : oi.getProduct().getName())
                        .mainImage(oi.getProduct().getMainImage())
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .lineTotal(oi.getLineTotal())
                        .build())
                .toList();

        return OrderDetailDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .placedAt(order.getPlacedAt())
                .itemsCount(items.size())
                .items(items)
                .build();
    }


    public Page<OrderSummaryDto> getMyOrders(String userEmail, int page, int limit) {

        User user = getUser(userEmail);

        if (page < 1) page = 1;
        if (limit < 1) limit = 10;

        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Direction.DESC, "placedAt"));

        return orders.findByUser(user, pageable)
                .map(this::toSummaryDto);
    }


    public OrderDetailDto getOrderByIdForUser(Long orderId, String userEmail) {
        User user = getUser(userEmail);

        Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not allowed to view this order");
        }

        return toDetailDto(order);
    }


    @Transactional
    public OrderDetailDto placeOrder(String userEmail, PlaceOrderRequest req) {
        User user = getUser(userEmail);

        Cart cart = carts.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("Cart not found for user"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        Address shippingAddress = addresses.findById(req.getAddressId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Address not found with id: " + req.getAddressId()));

        if (!shippingAddress.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Address does not belong to current user");
        }

        BigDecimal subTotal = cart.getItems().stream()
                .map(ci -> ci.getUnitPrice()
                        .multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;

        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            String code = req.getCouponCode().trim();

            Coupon coupon = coupons.findByCodeIgnoreCaseAndActiveTrue(code)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Coupon not found: " + code));

            LocalDate today = LocalDate.now();
            if ((coupon.getStartDate() != null && today.isBefore(coupon.getStartDate())) ||
                    (coupon.getExpiryDate() != null && today.isAfter(coupon.getExpiryDate()))) {
                throw new IllegalArgumentException("Coupon expired or not yet valid");
            }

            if (subTotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
                throw new IllegalArgumentException("Order amount is less than minimum required for this coupon");
            }

            if (coupon.getDiscountType() == DiscountType.FLAT) {
                discount = coupon.getDiscountAmount();
            } else {
                discount = subTotal
                        .multiply(coupon.getDiscountAmount())
                        .divide(BigDecimal.valueOf(100));
            }

            if (discount.compareTo(subTotal) > 0) {
                discount = subTotal;
            }
            appliedCoupon = coupon;
        }

        BigDecimal totalAmount = subTotal
                .add(shippingFee)
                .subtract(discount);

        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber("ORD-" + System.currentTimeMillis());
        order.setStatus(OrderStatus.PENDING);
        order.setSubTotal(subTotal);
        order.setShippingFee(shippingFee);
        order.setDiscount(discount);
        order.setTotalAmount(totalAmount);
        order.setPlacedAt(LocalDateTime.now());
        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(shippingAddress);
        order.setCoupon(appliedCoupon);

        for (CartItem ci : cart.getItems()) {
            Product p = ci.getProduct();

            if (p.getStockQty() < ci.getQuantity()) {
                throw new IllegalStateException(
                        "Not enough stock for product: " + p.getName()
                );
            }

            p.setStockQty(p.getStockQty() - ci.getQuantity());

            Product saved= products.save(p);
            appVectors.indexProduct(saved);


            BigDecimal lineTotal = ci.getUnitPrice()
                    .multiply(BigDecimal.valueOf(ci.getQuantity()));

            OrderItem oi = OrderItem.builder()
                    .productName(p.getName())
                    .sku(p.getSku())
                    .unitPrice(ci.getUnitPrice())
                    .quantity(ci.getQuantity())
                    .lineTotal(lineTotal)
                    .product(p)
                    .order(order)
                    .build();

            order.getItems().add(oi);
        }

        // Validate payment method
        String paymentMethodStr = req.getPaymentMethod().toUpperCase();

        // Only allow COD or ONLINE
        if (!paymentMethodStr.equals("COD") && !paymentMethodStr.equals("ONLINE")) {
            throw new IllegalArgumentException(
                    "Invalid payment method. Use 'COD' for cash on delivery or 'ONLINE' for online payment");
        }

        PaymentMethod method = PaymentMethod.valueOf(paymentMethodStr);

        // Create payment record
        Payment payment = Payment.builder()
                .method(method)
                .status(PaymentStatus.PENDING)
                .amount(totalAmount)
                .currency("INR")
                .provider(method == PaymentMethod.COD ? "Cash" : null)
                .order(order)
                .build();

        // For COD, confirm order immediately (payment remains PENDING until delivery)
        if (method == PaymentMethod.COD) {
            order.setStatus(OrderStatus.CONFIRMED);
        }

        order.setPayment(payment);

        Order saved = orders.save(order);

        // Index the order in the vector store
        appVectors.indexOrder(saved);

        // Clear cart
        cart.getItems().clear();
        cart.setTotalAmount(BigDecimal.ZERO);
        carts.save(cart);
        appVectors.indexOrder(saved);


        return toDetailDto(saved);
    }


    public Page<OrderSummaryDto> getOrdersForAdmin(String status,
                                                   int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1) limit = 10;

        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by("placedAt").descending());

        if (status == null || status.isBlank()) {
            return orders.findAll(pageable).map(this::toSummaryDto);
        }

        OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
        return orders.findByStatus(orderStatus, pageable)
                .map(this::toSummaryDto);
    }


    public OrderDetailDto getOrderByIdForAdmin(Long orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + orderId));

        return toDetailDto(order);
    }


    @Transactional
    public void updateOrderStatus(Long orderId, String status) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + orderId));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid status. Allowed values: PENDING, DELIVERED, CANCELLED");
        }

        order.setStatus(newStatus);

        orders.save(order);
    }
}
