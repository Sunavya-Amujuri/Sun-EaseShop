package com.telusko.controller;

import com.telusko.dto.OrderDetailDto;
import com.telusko.dto.OrderSummaryDto;
import com.telusko.dto.PlaceOrderRequest;
import com.telusko.dto.UpdateOrderStatusRequest;
import com.telusko.service.OrderService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer & admin order APIs")
public class OrderController {

    private final OrderService orderService;


    @PostMapping("/orders")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderDetailDto> placeOrder(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody PlaceOrderRequest request
    ) {
        OrderDetailDto dto = orderService.placeOrder(currentUser.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }


    @GetMapping("/profile/my-orders")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<OrderSummaryDto>> getMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Page<OrderSummaryDto> orders =
                orderService.getMyOrders(currentUser.getUsername(), page, limit);

        return ResponseEntity.ok(orders);
    }


    @GetMapping("/profile/my-orders/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderDetailDto> getMyOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        OrderDetailDto dto =
                orderService.getOrderByIdForUser(orderId, currentUser.getUsername());
        return ResponseEntity.ok(dto);
    }


    @GetMapping("/orders/list/admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<OrderSummaryDto>> getOrdersForAdmin(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Page<OrderSummaryDto> result =
                orderService.getOrdersForAdmin(status, page, limit);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/orders/{orderId}/admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderDetailDto> getOrderByIdForAdmin(
            @PathVariable Long orderId
    ) {
        OrderDetailDto dto = orderService.getOrderByIdForAdmin(orderId);
        return ResponseEntity.ok(dto);
    }


    @PatchMapping("/orders/status/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request
    ) {
        orderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(
                Map.of("message", "Order status updated successfully")
        );
    }
}
