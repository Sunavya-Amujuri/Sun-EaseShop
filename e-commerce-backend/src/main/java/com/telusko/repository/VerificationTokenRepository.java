package com.telusko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.User;
import com.telusko.model.VerificationToken;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * The user's current code.
     * <p>
     * Lookup is by user rather than by code, because the code is stored hashed and so cannot be
     * searched for. Only the newest row counts - issuing a new code deletes the old ones.
     */
    Optional<VerificationToken> findTopByUserOrderByIdDesc(User user);

    /** Clears earlier codes so only one is ever valid at a time. */
    void deleteByUser(User user);
}
