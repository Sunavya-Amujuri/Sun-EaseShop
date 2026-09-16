package com.telusko.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import com.telusko.dto.ApiErrorResponse;

import java.time.LocalDateTime;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)   // before the generic handler
@Slf4j
public class BusinessExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String errorCode = "BAD_REQUEST";

        if (message != null) {

            if (message.contains("token") || message.contains("Token")) {
                status = HttpStatus.UNAUTHORIZED;
                errorCode = "INVALID_TOKEN";
            }

            else if (message.contains("You do not own this ticket")) {
                status = HttpStatus.FORBIDDEN;
                errorCode = "TICKET_ACCESS_DENIED";
            }

            // e.g. TicketStatus.valueOf("XYZ") -> "No enum constant com.telusko.enums.TicketStatus.XYZ"
            else if (message.contains("No enum constant com.telusko.enums.TicketStatus")) {
                status = HttpStatus.BAD_REQUEST;
                errorCode = "INVALID_TICKET_STATUS";
                message = "Invalid ticket status value. Allowed values: OPEN, IN_PROGRESS, RESOLVED, CLOSED";
            }
        }

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex,
            WebRequest request
    ) {
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String errorCode = "INVALID_STATE";

        if (message != null && (message.contains("expired") || message.contains("revoked"))) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "TOKEN_EXPIRED";
        }

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            WebRequest request
    ) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("NOT_FOUND")
                .message(ex.getMessage())   // "User not found", "Order not found", "Ticket not found"
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            WebRequest request
    ) {
        // The raw cause echoes the failing SQL row (including the password hash), so it is
        // logged server-side only and never returned to the caller.
        log.warn("Data integrity violation on {}", request.getDescription(false),
                ex.getMostSpecificCause());

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("DATA_INTEGRITY_VIOLATION")
                .message("Request cannot be completed due to data integrity rules.")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 502: the request was fine, our payment provider was not. The message is already customer
     * facing - the provider's own wording was logged where it was caught.
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentGateway(
            PaymentGatewayException ex,
            WebRequest request
    ) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_GATEWAY.value())
                .error("PAYMENT_UNAVAILABLE")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(com.razorpay.RazorpayException.class)
    public ResponseEntity<ApiErrorResponse> handleRazorpay(
            com.razorpay.RazorpayException ex,
            WebRequest request
    ) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("PAYMENT_ERROR")
                .message("Payment failed: " + ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.badRequest().body(error);
    }
}
