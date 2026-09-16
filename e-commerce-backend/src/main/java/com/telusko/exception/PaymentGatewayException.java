package com.telusko.exception;

/**
 * The payment provider could not be used.
 * <p>
 * Carries a message already written for the customer - the provider's own error text is logged
 * server-side instead, because it describes our configuration rather than anything they did.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
