package com.telusko.service;

import com.razorpay.Order;
import com.razorpay.RazorpayException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telusko.dto.ApiMessage;
import com.telusko.dto.PaymentOrderResponse;
import com.telusko.dto.VerifyPaymentRequest;
import com.telusko.enums.OrderStatus;
import com.telusko.enums.PaymentMethod;
import com.telusko.enums.PaymentStatus;
import com.telusko.exception.PaymentGatewayException;
import com.telusko.model.Payment;
import com.telusko.model.User;
import com.telusko.repository.OrderRepository;
import com.telusko.repository.PaymentRepository;
import com.telusko.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RazorpayService razorpay;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final UserRepository users;

    @Transactional
    public PaymentOrderResponse createOrder(String email, Long orderId) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        com.telusko.model.Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        if (order.getPayment() != null && order.getPayment().getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Order already paid");
        }

        try {
            String receipt = "order_" + order.getOrderNumber();
            Order rzpOrder = razorpay.createOrder(order.getTotalAmount(), receipt);

            Payment payment = order.getPayment();
            if (payment == null) {
                payment = new Payment();
                payment.setOrder(order);
            }

            // Set method to CARD by default - will be updated after payment completion
            payment.setMethod(PaymentMethod.CARD);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency("INR");
            payment.setProvider("Razorpay");
            payment.setProviderRef(rzpOrder.get("id"));

            payments.save(payment);

            return PaymentOrderResponse.builder()
                    .razorpayOrderId(rzpOrder.get("id"))
                    .orderId(order.getId())
                    .amount(order.getTotalAmount().toString())
                    .currency("INR")
                    .keyId(razorpay.getKeyId())
                    .build();

        } catch (RazorpayException e) {
            // The gateway's own wording ("BAD_REQUEST_ERROR:Authentication failed") describes a
            // problem with our credentials, not with anything the customer did - showing it on
            // the checkout page just alarms them with a message they cannot act on. Log the real
            // reason for whoever is on call, and tell the customer something useful instead.
            log.error("Razorpay order creation failed for order {}: {}",
                    order.getOrderNumber(), e.getMessage());

            String message = isGatewayMisconfigured(e)
                    ? "Online payment is temporarily unavailable. Please choose Cash on Delivery, "
                      + "or try again shortly."
                    : "We could not start the payment. Please try again, or choose Cash on Delivery.";

            throw new PaymentGatewayException(message);
        }
    }

    /**
     * True when the gateway rejected us rather than the customer.
     * <p>
     * Authentication failures mean the API keys are missing, wrong or for the wrong mode - a
     * deployment problem. Retrying will not help, so the customer is steered to Cash on Delivery.
     */
    private boolean isGatewayMisconfigured(RazorpayException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return reason.contains("authentication") || reason.contains("unauthorized");
    }

    @Transactional
    public ApiMessage verifyPayment(String email, VerifyPaymentRequest req) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        com.telusko.model.Order order = orders.findById(req.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        Payment payment = order.getPayment();
        if (payment == null) {
            throw new EntityNotFoundException("Payment not found");
        }

        boolean valid = razorpay.verifySignature(
                req.getRazorpayOrderId(),
                req.getRazorpayPaymentId(),
                req.getRazorpaySignature()
        );

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Invalid signature");
            payments.save(payment);
            throw new IllegalStateException("Payment verification failed");
        }

        // Fetch payment details from Razorpay to get the actual payment method used
        try {
            com.razorpay.Payment rzpPayment = razorpay.fetchPayment(req.getRazorpayPaymentId());
            String methodUsed = rzpPayment.get("method"); // "card", "upi", "netbanking", "wallet"

            // Map Razorpay method to our enum
            PaymentMethod paymentMethod = mapRazorpayMethod(methodUsed);
            payment.setMethod(paymentMethod);

        } catch (RazorpayException e) {
            log.warn("Could not fetch payment method from Razorpay: {}", e.getMessage());
            // Keep default CARD if we can't fetch
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setProviderRef(req.getRazorpayPaymentId());
        payments.save(payment);

        order.setStatus(OrderStatus.CONFIRMED);
        orders.save(order);

        return new ApiMessage("Payment successful");
    }

    private PaymentMethod mapRazorpayMethod(String razorpayMethod) {
        if (razorpayMethod == null) return PaymentMethod.CARD;

        return switch (razorpayMethod.toLowerCase()) {
            case "card" -> PaymentMethod.CARD;
            case "upi" -> PaymentMethod.UPI;
            case "netbanking" -> PaymentMethod.NET_BANKING;
            case "wallet" -> PaymentMethod.WALLET;
            default -> PaymentMethod.CARD;
        };
    }

    @Transactional
    public ApiMessage handleFailure(String email, Long orderId, String reason) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        com.telusko.model.Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        Payment payment = order.getPayment();
        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason != null ? reason : "Payment failed");
            payments.save(payment);
        }

        return new ApiMessage("Payment failure recorded");
    }

    @Transactional
    public ApiMessage cancelPayment(String email, Long orderId) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        com.telusko.model.Order order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        Payment payment = order.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot cancel paid order");
        }

        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Cancelled by user");
            payments.save(payment);
        }

        order.setStatus(OrderStatus.CANCELED);
        orders.save(order);

        return new ApiMessage("Payment cancelled");
    }
}