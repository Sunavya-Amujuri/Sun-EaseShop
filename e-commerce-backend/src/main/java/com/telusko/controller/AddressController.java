package com.telusko.controller;

import com.telusko.model.Address;
import com.telusko.service.AddressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;


import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "Manage user addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<Address>> getAllAddresses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Page<Address> result = addressService.getAllAddresses(
                currentUser.getUsername(), page, limit);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Address> createAddress(
            @RequestBody Address address,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Address created = addressService.createAddress(
                currentUser.getUsername(), address);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{addressId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Address> getAddressById(
            @PathVariable Long addressId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Address address = addressService.getAddressById(
                currentUser.getUsername(), addressId);
        return ResponseEntity.ok(address);
    }

    @DeleteMapping("/{addressId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> deleteAddress(
            @PathVariable Long addressId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        addressService.deleteAddress(currentUser.getUsername(), addressId);
        return ResponseEntity.ok(Map.of("message", "Address deleted successfully"));
    }

    @PatchMapping("/{addressId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Address> updateAddress(
            @PathVariable Long addressId,
            @RequestBody Address address,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Address updated = addressService.updateAddress(
                currentUser.getUsername(), addressId, address);
        return ResponseEntity.ok(updated);
    }
}
