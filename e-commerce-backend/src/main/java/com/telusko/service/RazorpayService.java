package com.telusko.service;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;

@Service
@Slf4j
public class RazorpayService {

    private final RazorpayClient client;
    private final String keyId;
    private final String keySecret;

    public RazorpayService(@Value("${razorpay.api.key}") String keyId,
                           @Value("${razorpay.api.secret}") String keySecret) throws RazorpayException {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.client = new RazorpayClient(keyId, keySecret);
        log.info("Razorpay client initialized successfully");
    }

    public Order createOrder(BigDecimal amount, String receipt) throws RazorpayException {
        JSONObject options = new JSONObject();
        options.put("amount", amount.multiply(new BigDecimal("100")).intValue());
        options.put("currency", "INR");
        options.put("receipt", receipt);

        return client.orders.create(options);
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            boolean isValid = hex.toString().equals(signature);
            log.info("Signature verification: {}", isValid ? "SUCCESS" : "FAILED");
            return isValid;

        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    public Payment fetchPayment(String paymentId) throws RazorpayException {
        Payment payment = client.payments.fetch(paymentId);
        log.info("Fetched payment details for: {}", paymentId);
        return payment;
    }

    public String getKeyId() {
        return keyId;
    }
}