package com.telusko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.Cart;
import com.telusko.model.User;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(User user);
}