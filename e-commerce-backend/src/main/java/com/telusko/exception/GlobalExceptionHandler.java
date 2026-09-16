package com.telusko.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.telusko.dto.ApiErrorResponse;

import java.time.LocalDateTime;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)   // catch-all: must run last, after the specific handlers
@Slf4j
public class GlobalExceptionHandler {

    /**
     * An unmapped URL is a client mistake, not a server fault. Without this it falls through to
     * the catch-all below and is reported as 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            WebRequest request
    ) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("NOT_FOUND")
                .message("No endpoint found for the requested path.")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request
    ) {
        // Logged with the stack trace server-side; the raw message can expose internals, so it is
        // not echoed back to the caller.
        log.error("Unhandled exception on {}", request.getDescription(false), ex);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred.")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
