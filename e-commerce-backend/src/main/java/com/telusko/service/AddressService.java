package com.telusko.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.telusko.model.Address;
import com.telusko.model.User;
import com.telusko.repository.AddressRepository;
import com.telusko.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addresses;
    private final UserRepository users;
    private final AppVectorStoreService appVectors;


    private User getUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + email));
    }

    public Page<Address> getAllAddresses(String userEmail,
                                         int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1) limit = 10;

        User user = getUser(userEmail);
        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by("id").descending());

        return addresses.findByUser(user, pageable);
    }

    public Address createAddress(String userEmail, Address request) {
        User user = getUser(userEmail);

        Address address = Address.builder()
                .user(user)
                .line1(request.getLine1())
                .line2(request.getLine2())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pinCode(request.getPinCode())
                .label(request.getLabel())
                .isDefault(request.getIsDefault() != null
                        ? request.getIsDefault()
                        : false)
                .build();

        Address saved = addresses.save(address);
        appVectors.indexAddress(saved);


        return saved;
    }

    public Address getAddressById(String userEmail, Long addressId) {
        User user = getUser(userEmail);
        return addresses.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Address not found with id: " + addressId));
    }

    public void deleteAddress(String userEmail, Long addressId) {
        User user = getUser(userEmail);
        Address address = addresses.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Address not found with id: " + addressId));
        addresses.delete(address);

    }

    public Address updateAddress(String userEmail,
                                 Long addressId,
                                 Address request) {
        User user = getUser(userEmail);
        Address address = addresses.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Address not found with id: " + addressId));

        if (request.getLine1() != null) address.setLine1(request.getLine1());
        if (request.getLine2() != null) address.setLine2(request.getLine2());
        if (request.getCity() != null)  address.setCity(request.getCity());
        if (request.getState() != null) address.setState(request.getState());
        if (request.getCountry() != null) address.setCountry(request.getCountry());
        if (request.getPinCode() != null) address.setPinCode(request.getPinCode());
        if (request.getLabel() != null)   address.setLabel(request.getLabel());
        if (request.getIsDefault() != null) address.setIsDefault(request.getIsDefault());


        Address saved = addresses.save(address);
        appVectors.indexAddress(saved);


        return saved;
    }
}
