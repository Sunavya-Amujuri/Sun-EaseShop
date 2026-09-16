package com.telusko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.telusko.model.Coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Page<Coupon> findByActiveTrue(Pageable pageable);

    Optional<Coupon> findByIdAndActiveTrue(Long id);

    List<Coupon> findByActiveTrueAndStartDateBeforeAndExpiryDateAfter(
            LocalDateTime startBefore, LocalDateTime expiryAfter);

    Optional<Coupon> findByCodeIgnoreCaseAndActiveTrue(String code);
}
