package com.telusko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.PasswordResetToken;
import com.telusko.model.User;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** The user's current reset code. Lookup is by user because the code is stored hashed. */
    Optional<PasswordResetToken> findTopByUserOrderByIdDesc(User user);

    /** Clears earlier codes so only one is ever valid at a time. */
    void deleteByUser(User user);
}
