package com.telusko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.Address;
import com.telusko.model.User;

import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Page<Address> findByUser(User user, Pageable pageable);

    Optional<Address> findByIdAndUser(Long id, User user);
}
