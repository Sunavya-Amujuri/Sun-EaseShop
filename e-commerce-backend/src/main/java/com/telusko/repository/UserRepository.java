package com.telusko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
